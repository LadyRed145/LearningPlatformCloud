package com.duoc.learningplatformcloud.repository;

import com.duoc.learningplatformcloud.model.ResumenCompraMq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import java.util.Optional;

public interface ResumenCompraMqRepository extends JpaRepository<ResumenCompraMq, Long> {

    @Override
    @NonNull
    <S extends ResumenCompraMq> S save(@NonNull S entity);

    Optional<ResumenCompraMq> findByInscripcionId(Long inscripcionId);
}
