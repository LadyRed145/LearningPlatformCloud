package com.duoc.learningplatformcloud.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ResumenMqResponse(
        Long id,
        Long inscripcionId,
        String estudiante,
        LocalDateTime fechaInscripcion,
        List<CursoResumenResponse> cursos,
        Double total,
        MensajeriaResumenResponse mensajeria
) {
}
