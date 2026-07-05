package com.duoc.learningplatformcloud.service;

import com.duoc.learningplatformcloud.dto.ResumenMqMessage;
import com.duoc.learningplatformcloud.exception.RecursoNoEncontradoException;
import com.duoc.learningplatformcloud.model.DetalleInscripcion;
import com.duoc.learningplatformcloud.model.Inscripcion;
import com.duoc.learningplatformcloud.model.ResumenCompraMq;
import com.duoc.learningplatformcloud.repository.InscripcionRepository;
import com.duoc.learningplatformcloud.repository.ResumenCompraMqRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ResumenMqService {

    private final RabbitTemplate rabbitTemplate;
    private final InscripcionRepository inscripcionRepository;
    private final ResumenCompraMqRepository resumenCompraMqRepository;

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

        rabbitTemplate.convertAndSend(exchangeName, routingKey, mensaje);

        return mensaje;
    }

    @SuppressWarnings("null")
    @Transactional
    public ResumenCompraMq consumirResumenDesdeCola() {
        Object recibido = rabbitTemplate.receiveAndConvert(queueName);

        if (recibido == null) {
            throw new RecursoNoEncontradoException(
                    "No existen mensajes pendientes en la cola RabbitMQ."
            );
        }

        if (!(recibido instanceof ResumenMqMessage mensaje)) {
            throw new IllegalStateException(
                    "El mensaje recibido desde RabbitMQ tiene un formato inválido."
            );
        }

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

        return resumenCompraMqRepository.save(resumen);
    }

    @Transactional(readOnly = true)
    public List<ResumenCompraMq> listarResumenesGuardados() {
        return resumenCompraMqRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(
                        resumen -> Objects.requireNonNull(resumen.getId())
                ))
                .toList();
    }

    public Map<String, Object> estadoCola() {
        Long cantidad = rabbitTemplate.execute(
                channel -> channel.messageCount(queueName)
        );

        return Map.of(
                "queue", queueName,
                "mensajesPendientes", Objects.requireNonNullElse(cantidad, 0L),
                "exchange", exchangeName,
                "routingKey", routingKey,
                "estado", "OK"
        );
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
                .map(detalle -> construirLineaCurso(detalle))
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
                + " | Instructor: " + detalle.getCurso().getInstructor()
                + " | Duración: " + detalle.getCurso().getDuracionHoras() + " horas"
                + " | Costo: $" + detalle.getCostoCurso();
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
}
