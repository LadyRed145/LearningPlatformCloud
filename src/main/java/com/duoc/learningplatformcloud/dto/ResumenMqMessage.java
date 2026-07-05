package com.duoc.learningplatformcloud.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public record ResumenMqMessage(
        Long inscripcionId,
        String estudiante,
        Double total,
        LocalDateTime fechaInscripcion,
        List<String> cursos,
        String contenidoResumen,
        LocalDateTime fechaEnvio
) implements Serializable {
}
