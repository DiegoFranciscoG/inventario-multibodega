package io.github.diegofranciscog.inventory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.diegofranciscog.inventory.entity.UnitOfMeasure;

public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, String> {

    List<UnitOfMeasure> findAllByOrderByCodeAsc();
}
