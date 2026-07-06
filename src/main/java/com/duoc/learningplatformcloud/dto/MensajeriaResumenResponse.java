package com.duoc.learningplatformcloud.dto;

import java.time.LocalDateTime;

public record MensajeriaResumenResponse(
        String exchange,
        String routingKey,
        String queue,
        LocalDateTime fechaEnvio,
        LocalDateTime fechaConsumo,
        String estado
) {
}
