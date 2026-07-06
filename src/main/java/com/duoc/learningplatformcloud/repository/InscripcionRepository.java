package com.duoc.learningplatformcloud.repository;

import com.duoc.learningplatformcloud.model.Inscripcion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import java.util.List;

public interface InscripcionRepository extends JpaRepository<Inscripcion, Long> {

    @Override
    @NonNull
    @EntityGraph(attributePaths = {"detalles", "detalles.curso"})
    List<Inscripcion> findAll();
}