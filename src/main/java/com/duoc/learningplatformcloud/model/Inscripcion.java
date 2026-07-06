package com.duoc.learningplatformcloud.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "inscripciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String estudiante;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false)
    private Double total;

    @Column(name = "fecha_inscripcion", nullable = false)
    private LocalDateTime fechaInscripcion;

    @OneToMany(mappedBy = "inscripcion", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DetalleInscripcion> detalles = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (fechaInscripcion == null) {
            fechaInscripcion = LocalDateTime.now();
        }
    }

    public void agregarDetalle(DetalleInscripcion detalle) {
        detalle.setInscripcion(this);
        detalles.add(detalle);
    }
}
