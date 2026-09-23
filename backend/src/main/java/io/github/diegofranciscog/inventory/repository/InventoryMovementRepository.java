package io.github.diegofranciscog.inventory.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.diegofranciscog.inventory.entity.InventoryMovement;
import io.github.diegofranciscog.inventory.entity.MovementType;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    @Query(value = "select nextval('movement_number_seq')", nativeQuery = true)
    long nextNumber();

    @EntityGraph(attributePaths = {"warehouse", "targetWarehouse", "referenceType", "createdBy",
            "lines", "lines.product", "lines.location", "lines.lot", "lines.warehouse"})
    Optional<InventoryMovement> findWithLinesById(Long id);

    @Query(value = """
            select m from InventoryMovement m
            join fetch m.warehouse w
            left join fetch m.targetWarehouse tw
            join fetch m.createdBy u
            where (:type is null or m.type = :type)
              and (:warehouseId is null or w.id = :warehouseId or tw.id = :warehouseId)
              and m.occurredAt >= :from
              and m.occurredAt < :to
            """,
            countQuery = """
            select count(m) from InventoryMovement m
            left join m.targetWarehouse tw
            where (:type is null or m.type = :type)
              and (:warehouseId is null or m.warehouse.id = :warehouseId or tw.id = :warehouseId)
              and m.occurredAt >= :from
              and m.occurredAt < :to
            """)
    Page<InventoryMovement> search(@Param("type") MovementType type, @Param("warehouseId") Long warehouseId,
                                   @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    long countByOccurredAtGreaterThanEqual(Instant from);
}
