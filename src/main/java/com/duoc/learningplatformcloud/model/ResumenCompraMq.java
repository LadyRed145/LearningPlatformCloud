package com.duoc.learningplatformcloud.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "resumenes_compra_mq")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumenCompraMq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inscripcion_id", nullable = false)
    private Long inscripcionId;

    @Column(nullable = false, length = 120)
    private String estudiante;

    @Column(nullable = false)
    private Double total;

    @Lob
    @Column(name = "cursos_inscritos", nullable = false)
    private String cursosInscritos;

    @Lob
    @Column(name = "contenido_resumen", nullable = false)
    private String contenidoResumen;

    @Column(name = "fecha_inscripcion")
    private LocalDateTime fechaInscripcion;

    @Column(name = "fecha_envio_mq")
    private LocalDateTime fechaEnvioMq;

    @Column(name = "fecha_consumo_mq", nullable = false)
    private LocalDateTime fechaConsumoMq;

    @Column(nullable = false, length = 40)
    private String estado;

    @PrePersist
    public void prePersist() {
        if (fechaConsumoMq == null) {
            fechaConsumoMq = LocalDateTime.now();
        }

        if (estado == null || estado.isBlank()) {
            estado = "CONSUMIDO_GUARDADO";
        }
    }
}
