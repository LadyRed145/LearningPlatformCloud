package com.duoc.learningplatformcloud.service;

import com.duoc.learningplatformcloud.dto.CursoInscritoResponse;
import com.duoc.learningplatformcloud.dto.InscripcionRequest;
import com.duoc.learningplatformcloud.dto.InscripcionResponse;
import com.duoc.learningplatformcloud.exception.RecursoNoEncontradoException;
import com.duoc.learningplatformcloud.model.Curso;
import com.duoc.learningplatformcloud.model.DetalleInscripcion;
import com.duoc.learningplatformcloud.model.Inscripcion;
import com.duoc.learningplatformcloud.repository.CursoRepository;
import com.duoc.learningplatformcloud.repository.InscripcionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InscripcionService {

    private final CursoRepository cursoRepository;
    private final InscripcionRepository inscripcionRepository;

    @Transactional(readOnly = true)
    public List<InscripcionResponse> listarInscripciones() {
        return inscripcionRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(inscripcion -> Objects.requireNonNull(inscripcion.getId())))
                .map(inscripcion -> mapearInscripcionResponse(inscripcion))
                .toList();
    }

    @Transactional
    public InscripcionResponse inscribirEstudiante(InscripcionRequest request) {
        Objects.requireNonNull(request, "La solicitud de inscripción no puede ser nula.");

        List<Long> cursosIds = Objects.requireNonNull(
                request.cursosIds(),
                "La lista de cursos no puede ser nula."
        );

        if (cursosIds.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos un curso.");
        }

        List<Curso> cursos = cursoRepository.findAllById(cursosIds);

        if (cursos.size() != cursosIds.size()) {
            throw new RecursoNoEncontradoException("Uno o más cursos seleccionados no existen.");
        }

        double total = cursos.stream()
                .mapToDouble(curso -> Objects.requireNonNull(curso.getCosto()))
                .sum();

        Inscripcion inscripcion = Inscripcion.builder()
                .estudiante(request.estudiante())
                .total(total)
                .build();

        List<DetalleInscripcion> detalles = cursos.stream()
                .map(curso -> DetalleInscripcion.builder()
                        .inscripcion(inscripcion)
                        .curso(curso)
                        .costoCurso(curso.getCosto())
                        .build())
                .toList();

        inscripcion.getDetalles().addAll(detalles);

        Inscripcion inscripcionGuardada = inscripcionRepository.save(inscripcion);

        return mapearInscripcionResponse(inscripcionGuardada);
    }

    private InscripcionResponse mapearInscripcionResponse(Inscripcion inscripcion) {
        List<CursoInscritoResponse> cursos = inscripcion.getDetalles()
                .stream()
                .map(detalle -> new CursoInscritoResponse(
                        detalle.getCurso().getId(),
                        detalle.getCurso().getNombre(),
                        detalle.getCurso().getInstructor(),
                        detalle.getCurso().getDuracionHoras(),
                        detalle.getCostoCurso()
                ))
                .toList();

        return new InscripcionResponse(
                inscripcion.getId(),
                inscripcion.getEstudiante(),
                cursos,
                inscripcion.getTotal(),
                inscripcion.getFechaInscripcion()
        );
    }
}