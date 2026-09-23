package io.github.diegofranciscog.inventory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import io.github.diegofranciscog.inventory.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @EntityGraph(attributePaths = "warehouses")
    Optional<AppUser> findByEmail(String email);

    @EntityGraph(attributePaths = "warehouses")
    Optional<AppUser> findWithWarehousesById(Long id);

    @EntityGraph(attributePaths = "warehouses")
    List<AppUser> findAllByOrderByEmailAsc();

    boolean existsByEmail(String email);
}
