package io.github.diegofranciscog.inventory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.diegofranciscog.inventory.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByCode(String code);

    List<Category> findAllByOrderByNameAsc();
}
