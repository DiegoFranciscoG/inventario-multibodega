package io.github.diegofranciscog.inventory.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.diegofranciscog.inventory.entity.MovementLine;

public interface MovementLineRepository extends JpaRepository<MovementLine, Long> {

    String KARDEX_FILTER = """
            where l.product.id = :productId
              and (:warehouseId is null or l.warehouse.id = :warehouseId)
              and l.occurredAt >= :from
              and l.occurredAt < :to
            """;

    /** Kárdex en orden de registro (el id es la secuencia cronológica, R-06). */
    @Query(value = """
            select l from MovementLine l
            join fetch l.movement m
            join fetch m.referenceType
            join fetch l.warehouse
            join fetch l.location
            left join fetch l.lot
            """ + KARDEX_FILTER + " order by l.id asc",
            countQuery = "select count(l) from MovementLine l " + KARDEX_FILTER)
    Page<MovementLine> findKardex(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId,
                                  @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("""
            select new io.github.diegofranciscog.inventory.repository.MovementLineRepository$Totals(
                coalesce(sum(case when l.direction = io.github.diegofranciscog.inventory.entity.Direction.IN then l.quantity else 0 end), 0),
                coalesce(sum(case when l.direction = io.github.diegofranciscog.inventory.entity.Direction.IN then l.totalCost else 0 end), 0),
                coalesce(sum(case when l.direction = io.github.diegofranciscog.inventory.entity.Direction.OUT then l.quantity else 0 end), 0),
                coalesce(sum(case when l.direction = io.github.diegofranciscog.inventory.entity.Direction.OUT then l.totalCost else 0 end), 0))
            from MovementLine l
            """ + KARDEX_FILTER)
    Totals totals(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId,
                  @Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(l) from MovementLine l " + KARDEX_FILTER)
    long countKardex(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId,
                     @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select l from MovementLine l
            join fetch l.product
            join fetch l.warehouse
            join fetch l.location
            left join fetch l.lot
            where l.movement.id = :movementId
            order by l.lineNo
            """)
    List<MovementLine> findByMovement(@Param("movementId") Long movementId);

    record Totals(BigDecimal inQuantity, BigDecimal inValue, BigDecimal outQuantity, BigDecimal outValue) {
    }
}
