package com.duoc.learningplatformcloud.controller;

import com.duoc.learningplatformcloud.dto.ResumenMqMessage;
import com.duoc.learningplatformcloud.dto.ResumenMqResponse;
import com.duoc.learningplatformcloud.model.ResumenCompraMq;
import com.duoc.learningplatformcloud.service.ResumenMqService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mq/resumenes")
@RequiredArgsConstructor
public class ResumenMqController {

    private final ResumenMqService resumenMqService;

    @PostMapping("/{inscripcionId}/enviar")
    public ResponseEntity<Map<String, Object>> enviarResumenACola(@PathVariable Long inscripcionId) {
        ResumenMqMessage mensaje = resumenMqService.enviarResumenACola(inscripcionId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("mensaje", "Resumen de inscripción enviado correctamente a RabbitMQ.");
        response.put("accion", "ENVIAR_COLA_MQ");
        response.put("cola", "learning.resumenes.queue");
        response.put("inscripcionId", mensaje.inscripcionId());
        response.put("estudiante", mensaje.estudiante());
        response.put("total", mensaje.total());
        response.put("fechaEnvio", mensaje.fechaEnvio());
        response.put("s3Actualizado", true);
        response.put("archivosS3", resumenMqService.keysS3RabbitMq());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/consumir")
    public ResponseEntity<Map<String, Object>> consumirResumenDesdeCola() {
        ResumenCompraMq resumenGuardado = resumenMqService.consumirResumenDesdeCola();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("mensaje", "Resumen consumido desde RabbitMQ y guardado en Oracle Cloud.");
        response.put("accion", "CONSUMIR_COLA_GUARDAR_ORACLE");
        response.put("idResumenGuardado", resumenGuardado.getId());
        response.put("inscripcionId", resumenGuardado.getInscripcionId());
        response.put("estudiante", resumenGuardado.getEstudiante());
        response.put("total", resumenGuardado.getTotal());
        response.put("estado", resumenGuardado.getEstado());
        response.put("fechaConsumoMq", resumenGuardado.getFechaConsumoMq());
        response.put("s3Actualizado", true);
        response.put("archivosS3", resumenMqService.keysS3RabbitMq());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<ResumenMqResponse> listarResumenesGuardados() {
        return resumenMqService.listarResumenesGuardados();
    }

    @GetMapping("/estado-cola")
    public Map<String, Object> estadoCola() {
        return resumenMqService.estadoCola();
    }
}
