package com.duoc.learningplatformcloud.service;

import com.duoc.learningplatformcloud.dto.CursoResumenResponse;
import com.duoc.learningplatformcloud.dto.MensajeriaResumenResponse;
import com.duoc.learningplatformcloud.dto.ResumenMqMessage;
import com.duoc.learningplatformcloud.dto.ResumenMqResponse;
import com.duoc.learningplatformcloud.exception.RecursoNoEncontradoException;
import com.duoc.learningplatformcloud.model.DetalleInscripcion;
import com.duoc.learningplatformcloud.model.Inscripcion;
import com.duoc.learningplatformcloud.model.ResumenCompraMq;
import com.duoc.learningplatformcloud.repository.InscripcionRepository;
import com.duoc.learningplatformcloud.repository.ResumenCompraMqRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ResumenMqService {

    private static final String SEPARADOR_CAMPO_CURSO = " | ";
    private static final String PREFIJO_INSTRUCTOR = "Instructor: ";
    private static final String PREFIJO_DURACION = "Duración: ";
    private static final String PREFIJO_COSTO = "Costo: $";

    private static final String S3_KEY_ULTIMO_ENVIO = "mq/ultimo-envio.json";
    private static final String S3_KEY_ESTADO_COLA = "mq/estado-cola.json";
    private static final String S3_KEY_ULTIMO_CONSUMO = "mq/ultimo-consumo.json";
    private static final String S3_KEY_RESUMENES_CONSUMIDOS = "mq/resumenes-consumidos.json";
    private static final String S3_KEY_EVIDENCIA_RABBITMQ = "mq/evidencia-rabbitmq.txt";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final InscripcionRepository inscripcionRepository;
    private final ResumenCompraMqRepository resumenCompraMqRepository;
    private final S3ResumenService s3ResumenService;

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.routing-key}")
    private String routingKey;

    @Value("${app.rabbitmq.queue}")
    private String queueName;

    @Transactional(readOnly = true)
    public ResumenMqMessage enviarResumenACola(Long inscripcionId) {
        Inscripcion inscripcion = buscarInscripcion(inscripcionId);
        ResumenMqMessage mensaje = construirMensaje(inscripcion);
        String mensajeJson = convertirMensajeAJson(mensaje);

        rabbitTemplate.convertAndSend(
                exchangeName,
                routingKey,
                mensajeJson,
                message -> {
                    message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    message.getMessageProperties().setType("resumenMqMessage");
                    return message;
                }
        );

        Map<String, Object> respuestaEnvio = construirRespuestaEnvio(mensaje);
        Map<String, Object> estadoCola = consultarEstadoCola();

        subirJsonS3(S3_KEY_ULTIMO_ENVIO, respuestaEnvio);
        subirJsonS3(S3_KEY_ESTADO_COLA, estadoCola);
        subirTextoS3(S3_KEY_EVIDENCIA_RABBITMQ, construirEvidenciaRabbitMq());

        return mensaje;
    }

    @Transactional
    public ResumenCompraMq consumirResumenDesdeCola() {
        Object recibido = rabbitTemplate.receiveAndConvert(queueName);

        if (recibido == null) {
            throw new RecursoNoEncontradoException(
                    "No existen mensajes pendientes en la cola RabbitMQ."
            );
        }

        ResumenMqMessage mensaje = convertirMensajeDesdeCola(recibido);

        ResumenCompraMq resumen = ResumenCompraMq.builder()
                .inscripcionId(mensaje.inscripcionId())
                .estudiante(mensaje.estudiante())
                .total(mensaje.total())
                .cursosInscritos(String.join(System.lineSeparator(), mensaje.cursos()))
                .contenidoResumen(mensaje.contenidoResumen())
                .fechaInscripcion(mensaje.fechaInscripcion())
                .fechaEnvioMq(mensaje.fechaEnvio())
                .fechaConsumoMq(LocalDateTime.now())
                .estado("CONSUMIDO_GUARDADO")
                .build();

        ResumenCompraMq resumenGuardado = resumenCompraMqRepository.save(resumen);

        Map<String, Object> respuestaConsumo = construirRespuestaConsumo(resumenGuardado);
        List<ResumenMqResponse> resumenes = obtenerResumenesGuardadosOrdenados();
        Map<String, Object> estadoCola = consultarEstadoCola();

        subirJsonS3(S3_KEY_ULTIMO_CONSUMO, respuestaConsumo);
        subirJsonS3(S3_KEY_RESUMENES_CONSUMIDOS, resumenes);
        subirJsonS3(S3_KEY_ESTADO_COLA, estadoCola);
        subirTextoS3(S3_KEY_EVIDENCIA_RABBITMQ, construirEvidenciaRabbitMq());

        return resumenGuardado;
    }

    @Transactional(readOnly = true)
    public List<ResumenMqResponse> listarResumenesGuardados() {
        List<ResumenMqResponse> resumenes = obtenerResumenesGuardadosOrdenados();

        subirJsonS3(S3_KEY_RESUMENES_CONSUMIDOS, resumenes);
        subirTextoS3(S3_KEY_EVIDENCIA_RABBITMQ, construirEvidenciaRabbitMq());

        return resumenes;
    }

    public Map<String, Object> estadoCola() {
        Map<String, Object> estadoCola = consultarEstadoCola();

        subirJsonS3(S3_KEY_ESTADO_COLA, estadoCola);
        subirTextoS3(S3_KEY_EVIDENCIA_RABBITMQ, construirEvidenciaRabbitMq());

        return estadoCola;
    }

    public Map<String, String> keysS3RabbitMq() {
        return Map.of(
                "ultimoEnvio", S3_KEY_ULTIMO_ENVIO,
                "estadoCola", S3_KEY_ESTADO_COLA,
                "ultimoConsumo", S3_KEY_ULTIMO_CONSUMO,
                "resumenesConsumidos", S3_KEY_RESUMENES_CONSUMIDOS,
                "evidenciaRabbitMq", S3_KEY_EVIDENCIA_RABBITMQ
        );
    }

    private List<ResumenMqResponse> obtenerResumenesGuardadosOrdenados() {
        return resumenCompraMqRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(
                        resumen -> Objects.requireNonNullElse(resumen.getId(), 0L)
                ))
                .map(this::convertirARespuestaOrdenada)
                .toList();
    }

    private Map<String, Object> consultarEstadoCola() {
        Long cantidad = rabbitTemplate.execute(
                channel -> channel.messageCount(queueName)
        );

        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("exchange", exchangeName);
        estado.put("routingKey", routingKey);
        estado.put("queue", queueName);
        estado.put("mensajesPendientes", Objects.requireNonNullElse(cantidad, 0L));
        estado.put("estado", "OK");
        estado.put("fechaActualizacionS3", LocalDateTime.now());

        return estado;
    }

    private Map<String, Object> construirRespuestaEnvio(ResumenMqMessage mensaje) {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("mensaje", "Resumen de inscripción enviado correctamente a RabbitMQ.");
        respuesta.put("accion", "ENVIAR_COLA_MQ");
        respuesta.put("cola", queueName);
        respuesta.put("exchange", exchangeName);
        respuesta.put("routingKey", routingKey);
        respuesta.put("inscripcionId", mensaje.inscripcionId());
        respuesta.put("estudiante", mensaje.estudiante());
        respuesta.put("total", mensaje.total());
        respuesta.put("fechaEnvio", mensaje.fechaEnvio());
        respuesta.put("s3Actualizado", true);
        respuesta.put("s3Key", S3_KEY_ULTIMO_ENVIO);

        return respuesta;
    }

    private Map<String, Object> construirRespuestaConsumo(ResumenCompraMq resumenGuardado) {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("mensaje", "Resumen consumido desde RabbitMQ y guardado en Oracle Cloud.");
        respuesta.put("accion", "CONSUMIR_COLA_GUARDAR_ORACLE");
        respuesta.put("idResumenGuardado", resumenGuardado.getId());
        respuesta.put("inscripcionId", resumenGuardado.getInscripcionId());
        respuesta.put("estudiante", resumenGuardado.getEstudiante());
        respuesta.put("total", resumenGuardado.getTotal());
        respuesta.put("estado", resumenGuardado.getEstado());
        respuesta.put("fechaConsumoMq", resumenGuardado.getFechaConsumoMq());
        respuesta.put("s3Actualizado", true);
        respuesta.put("s3Key", S3_KEY_ULTIMO_CONSUMO);

        return respuesta;
    }

    private String construirEvidenciaRabbitMq() {
        StringBuilder evidencia = new StringBuilder();

        evidencia.append("EVIDENCIA RABBITMQ + ORACLE + S3").append(System.lineSeparator());
        evidencia.append("================================").append(System.lineSeparator());
        evidencia.append("Fecha actualización: ").append(LocalDateTime.now()).append(System.lineSeparator());
        evidencia.append("Exchange: ").append(exchangeName).append(System.lineSeparator());
        evidencia.append("Routing key: ").append(routingKey).append(System.lineSeparator());
        evidencia.append("Queue: ").append(queueName).append(System.lineSeparator());
        evidencia.append(System.lineSeparator());
        evidencia.append("Archivos sincronizados en S3:").append(System.lineSeparator());
        evidencia.append("- ").append(S3_KEY_ULTIMO_ENVIO).append(System.lineSeparator());
        evidencia.append("- ").append(S3_KEY_ESTADO_COLA).append(System.lineSeparator());
        evidencia.append("- ").append(S3_KEY_ULTIMO_CONSUMO).append(System.lineSeparator());
        evidencia.append("- ").append(S3_KEY_RESUMENES_CONSUMIDOS).append(System.lineSeparator());
        evidencia.append("- ").append(S3_KEY_EVIDENCIA_RABBITMQ).append(System.lineSeparator());

        return evidencia.toString();
    }

    private void subirJsonS3(String key, Object contenido) {
        try {
            String json = objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(contenido);

            s3ResumenService.subirJson(key, json);

        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No fue posible generar el JSON para sincronizar S3: " + key, ex);
        }
    }

    private void subirTextoS3(String key, String contenido) {
        s3ResumenService.subirTexto(key, contenido);
    }

    private Inscripcion buscarInscripcion(Long inscripcionId) {
        if (inscripcionId == null) {
            throw new IllegalArgumentException("El ID de inscripción no puede ser nulo.");
        }

        return inscripcionRepository.findById(inscripcionId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una inscripción con ID: " + inscripcionId
                ));
    }

    private ResumenMqMessage construirMensaje(Inscripcion inscripcion) {
        List<String> cursos = inscripcion.getDetalles()
                .stream()
                .map(this::construirLineaCurso)
                .toList();

        String contenido = construirContenidoResumen(inscripcion, cursos);

        return new ResumenMqMessage(
                inscripcion.getId(),
                inscripcion.getEstudiante(),
                inscripcion.getTotal(),
                inscripcion.getFechaInscripcion(),
                cursos,
                contenido,
                LocalDateTime.now()
        );
    }

    private String construirLineaCurso(DetalleInscripcion detalle) {
        return detalle.getCurso().getNombre()
                + SEPARADOR_CAMPO_CURSO + PREFIJO_INSTRUCTOR + detalle.getCurso().getInstructor()
                + SEPARADOR_CAMPO_CURSO + PREFIJO_DURACION + detalle.getCurso().getDuracionHoras() + " horas"
                + SEPARADOR_CAMPO_CURSO + PREFIJO_COSTO + detalle.getCostoCurso();
    }

    private String construirContenidoResumen(Inscripcion inscripcion, List<String> cursos) {
        StringBuilder resumen = new StringBuilder();

        resumen.append("RESUMEN DE COMPRA / INSCRIPCIÓN MQ").append(System.lineSeparator());
        resumen.append("==================================").append(System.lineSeparator());
        resumen.append("Inscripción ID: ").append(inscripcion.getId()).append(System.lineSeparator());
        resumen.append("Estudiante: ").append(inscripcion.getEstudiante()).append(System.lineSeparator());
        resumen.append("Fecha inscripción: ").append(inscripcion.getFechaInscripcion()).append(System.lineSeparator());
        resumen.append(System.lineSeparator());
        resumen.append("Cursos inscritos:").append(System.lineSeparator());

        cursos.forEach(curso -> resumen.append("- ").append(curso).append(System.lineSeparator()));

        resumen.append(System.lineSeparator());
        resumen.append("Total compra: $").append(inscripcion.getTotal()).append(System.lineSeparator());
        resumen.append("Evento: Mensaje enviado a RabbitMQ para consumo asíncrono.").append(System.lineSeparator());

        return resumen.toString();
    }

    private String convertirMensajeAJson(ResumenMqMessage mensaje) {
        try {
            return objectMapper.writeValueAsString(mensaje);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No fue posible convertir el mensaje RabbitMQ a JSON.", ex);
        }
    }

    private ResumenMqMessage convertirMensajeDesdeCola(Object recibido) {
        try {
            if (recibido instanceof ResumenMqMessage mensaje) {
                return mensaje;
            }

            if (recibido instanceof String mensajeJson) {
                return objectMapper.readValue(mensajeJson, ResumenMqMessage.class);
            }

            if (recibido instanceof byte[] bytes) {
                String mensajeJson = new String(bytes, StandardCharsets.UTF_8);
                return objectMapper.readValue(mensajeJson, ResumenMqMessage.class);
            }

            throw new IllegalStateException(
                    "El mensaje recibido desde RabbitMQ tiene un formato inválido: "
                            + recibido.getClass().getName()
            );

        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No fue posible leer el mensaje JSON recibido desde RabbitMQ.", ex);
        }
    }

    private ResumenMqResponse convertirARespuestaOrdenada(ResumenCompraMq resumen) {
        return new ResumenMqResponse(
                resumen.getId(),
                resumen.getInscripcionId(),
                resumen.getEstudiante(),
                resumen.getFechaInscripcion(),
                convertirCursosARespuesta(resumen.getCursosInscritos()),
                resumen.getTotal(),
                new MensajeriaResumenResponse(
                        exchangeName,
                        routingKey,
                        queueName,
                        resumen.getFechaEnvioMq(),
                        resumen.getFechaConsumoMq(),
                        resumen.getEstado()
                )
        );
    }

    private List<CursoResumenResponse> convertirCursosARespuesta(String cursosInscritos) {
        if (cursosInscritos == null || cursosInscritos.isBlank()) {
            return List.of();
        }

        return cursosInscritos.lines()
                .map(String::trim)
                .filter(linea -> !linea.isBlank())
                .map(this::convertirLineaCursoARespuesta)
                .toList();
    }

    private CursoResumenResponse convertirLineaCursoARespuesta(String lineaCurso) {
        String[] partes = lineaCurso.split("\\Q" + SEPARADOR_CAMPO_CURSO + "\\E");

        String nombre = obtenerParte(partes, 0);
        String instructor = limpiarPrefijo(obtenerParte(partes, 1), PREFIJO_INSTRUCTOR);
        Integer duracionHoras = convertirDuracion(limpiarPrefijo(obtenerParte(partes, 2), PREFIJO_DURACION));
        Double costo = convertirCosto(limpiarPrefijo(obtenerParte(partes, 3), PREFIJO_COSTO));

        return new CursoResumenResponse(
                nombre,
                instructor,
                duracionHoras,
                costo
        );
    }

    private String obtenerParte(String[] partes, int indice) {
        if (partes == null || indice < 0 || indice >= partes.length) {
            return "";
        }

        return partes[indice] == null ? "" : partes[indice].trim();
    }

    private String limpiarPrefijo(String valor, String prefijo) {
        if (valor == null || valor.isBlank()) {
            return "";
        }

        return valor.replace(prefijo, "").trim();
    }

    private Integer convertirDuracion(String valor) {
        if (valor == null || valor.isBlank()) {
            return 0;
        }

        String soloNumero = valor
                .replace("horas", "")
                .replace("hora", "")
                .trim();

        try {
            return Integer.parseInt(soloNumero);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private Double convertirCosto(String valor) {
        if (valor == null || valor.isBlank()) {
            return 0.0;
        }

        String soloNumero = valor
                .replace("$", "")
                .replace(",", ".")
                .trim();

        try {
            return Double.parseDouble(soloNumero);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }
}
