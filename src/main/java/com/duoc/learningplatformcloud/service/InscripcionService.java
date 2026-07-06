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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class InscripcionService {

    private final CursoRepository cursoRepository;
    private final InscripcionRepository inscripcionRepository;

    @Transactional(readOnly = true)
    public List<InscripcionResponse> listarInscripciones() {
        return inscripcionRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(inscripcion -> Objects.requireNonNullElse(inscripcion.getId(), 0L)))
                .map(this::mapearInscripcionResponse)
                .toList();
    }

    @Transactional
    public InscripcionResponse inscribirEstudiante(InscripcionRequest request) {
        validarSolicitud(request);

        String estudiante = request.estudiante().trim();
        List<Long> cursosIds = normalizarCursosIds(request.cursosIds());

        List<Curso> cursos = cursoRepository.findAllById(cursosIds);

        if (cursos.size() != cursosIds.size()) {
            throw new RecursoNoEncontradoException("Uno o más cursos seleccionados no existen.");
        }

        List<Curso> cursosOrdenados = ordenarCursosSegunRequest(cursos, cursosIds);

        double total = cursosOrdenados.stream()
                .map(Curso::getCosto)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        Inscripcion inscripcion = Inscripcion.builder()
                .estudiante(estudiante)
                .total(total)
                .build();

        for (Curso curso : cursosOrdenados) {
            DetalleInscripcion detalle = DetalleInscripcion.builder()
                    .curso(curso)
                    .costoCurso(Objects.requireNonNullElse(curso.getCosto(), 0.0))
                    .build();

            inscripcion.agregarDetalle(detalle);
        }

        Inscripcion inscripcionGuardada = inscripcionRepository.saveAndFlush(inscripcion);
        return mapearInscripcionResponse(inscripcionGuardada);
    }

    private void validarSolicitud(InscripcionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de inscripción no puede ser nula.");
        }

        if (request.estudiante() == null || request.estudiante().isBlank()) {
            throw new IllegalArgumentException("El nombre del estudiante es obligatorio.");
        }

        if (request.cursosIds() == null || request.cursosIds().isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos un curso.");
        }
    }

    private List<Long> normalizarCursosIds(List<Long> cursosIds) {
        Set<Long> cursosUnicos = new LinkedHashSet<>();

        for (Long cursoId : cursosIds) {
            if (cursoId == null || cursoId <= 0) {
                throw new IllegalArgumentException("Los IDs de cursos deben ser números válidos.");
            }

            cursosUnicos.add(cursoId);
        }

        return cursosUnicos.stream().toList();
    }

    private List<Curso> ordenarCursosSegunRequest(List<Curso> cursos, List<Long> cursosIds) {
        return cursosIds.stream()
                .map(cursoId -> cursos.stream()
                        .filter(curso -> Objects.equals(curso.getId(), cursoId))
                        .findFirst()
                        .orElseThrow(() -> new RecursoNoEncontradoException(
                                "No existe un curso con ID: " + cursoId
                        )))
                .toList();
    }

    private InscripcionResponse mapearInscripcionResponse(Inscripcion inscripcion) {
        List<CursoInscritoResponse> cursos = inscripcion.getDetalles()
                .stream()
                .sorted(Comparator.comparing(detalle -> Objects.requireNonNullElse(detalle.getId(), 0L)))
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