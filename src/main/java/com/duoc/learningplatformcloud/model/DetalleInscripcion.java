package com.duoc.learningplatformcloud.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "detalle_inscripcion",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_detalle_inscripcion_curso",
                        columnNames = {"inscripcion_id", "curso_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetalleInscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "inscripcion_id", nullable = false)
    private Inscripcion inscripcion;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "curso_id", nullable = false)
    private Curso curso;

    @Column(name = "costo_curso", nullable = false)
    private Double costoCurso;
}