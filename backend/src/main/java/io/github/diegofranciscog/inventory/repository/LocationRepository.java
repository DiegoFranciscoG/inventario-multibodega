package io.github.diegofranciscog.inventory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.diegofranciscog.inventory.entity.Location;

public interface LocationRepository extends JpaRepository<Location, Long> {

    @Query("select l from Location l join fetch l.warehouse w where w.id = :warehouseId order by l.code")
    List<Location> findByWarehouse(@Param("warehouseId") Long warehouseId);

    @Query("select l from Location l join fetch l.warehouse w where w.code = :warehouseCode and l.code = :code")
    Optional<Location> findByWarehouseCodeAndCode(@Param("warehouseCode") String warehouseCode, @Param("code") String code);

    boolean existsByWarehouseIdAndCode(Long warehouseId, String code);
}
