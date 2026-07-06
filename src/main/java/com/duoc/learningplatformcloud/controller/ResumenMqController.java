package com.duoc.learningplatformcloud.controller;

import com.duoc.learningplatformcloud.dto.ResumenMqMessage;
import com.duoc.learningplatformcloud.dto.ResumenMqResponse;
import com.duoc.learningplatformcloud.model.ResumenCompraMq;
import com.duoc.learningplatformcloud.service.ResumenMqService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "mensaje", "Resumen de inscripción enviado correctamente a RabbitMQ.",
                "accion", "ENVIAR_COLA_MQ",
                "cola", "learning.resumenes.queue",
                "inscripcionId", mensaje.inscripcionId(),
                "estudiante", mensaje.estudiante(),
                "total", mensaje.total(),
                "fechaEnvio", mensaje.fechaEnvio()
        ));
    }

    @PostMapping("/consumir")
    public ResponseEntity<Map<String, Object>> consumirResumenDesdeCola() {
        ResumenCompraMq resumenGuardado = resumenMqService.consumirResumenDesdeCola();

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "mensaje", "Resumen consumido desde RabbitMQ y guardado en Oracle Cloud.",
                "accion", "CONSUMIR_COLA_GUARDAR_ORACLE",
                "idResumenGuardado", resumenGuardado.getId(),
                "inscripcionId", resumenGuardado.getInscripcionId(),
                "estudiante", resumenGuardado.getEstudiante(),
                "total", resumenGuardado.getTotal(),
                "estado", resumenGuardado.getEstado(),
                "fechaConsumoMq", resumenGuardado.getFechaConsumoMq()
        ));
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
