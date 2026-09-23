package io.github.diegofranciscog.inventory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.diegofranciscog.inventory.entity.DocumentType;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, String> {

    List<DocumentType> findAllByOrderBySourceDescCodeAsc();
}
