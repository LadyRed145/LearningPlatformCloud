package com.duoc.learningplatformcloud.dto;

public record CursoResumenResponse(
        String nombre,
        String instructor,
        Integer duracionHoras,
        Double costo
) {
}
