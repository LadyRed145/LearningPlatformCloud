package com.duoc.learningplatformcloud.repository;

import com.duoc.learningplatformcloud.model.Inscripcion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InscripcionRepository extends JpaRepository<Inscripcion, Long> {

    @Override
    @EntityGraph(attributePaths = {"detalles", "detalles.curso"})
    List<Inscripcion> findAll();
}