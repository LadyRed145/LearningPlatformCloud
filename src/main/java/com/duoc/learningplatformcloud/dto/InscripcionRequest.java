package com.duoc.learningplatformcloud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record InscripcionRequest(
        @NotBlank(message = "El nombre del estudiante es obligatorio.")
        String estudiante,

        @NotEmpty(message = "Debe seleccionar al menos un curso.")
        List<Long> cursosIds
) {
}