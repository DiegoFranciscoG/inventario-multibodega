package io.github.diegofranciscog.inventory.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.diegofranciscog.inventory.entity.AbcClass;
import io.github.diegofranciscog.inventory.entity.Stock;

public interface StockRepository extends JpaRepository<Stock, Long> {

    /**
     * Garantiza que exista la fila de stock para (producto, ubicación, lote). Con {@code ON CONFLICT DO NOTHING} dos
     * transacciones que crean la misma fila a la vez no fallan: la segunda espera y reutiliza la fila.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into stock (product_id, warehouse_id, location_id, lot_id, quantity)
            values (:productId, :warehouseId, :locationId, cast(:lotId as bigint), 0)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId,
                       @Param("locationId") Long locationId, @Param("lotId") Long lotId);

    Optional<Stock> findByProductIdAndLocationIdAndLotIsNull(Long productId, Long locationId);

    Optional<Stock> findByProductIdAndLocationIdAndLotId(Long productId, Long locationId, Long lotId);

    /**
     * Candidatos FEFO (R-18): primero lo que vence antes, sin lotes vencidos; lo que no tiene lote va al final.
     */
    @Query("""
            select s from Stock s
            join fetch s.location loc
            left join fetch s.lot lot
            where s.product.id = :productId
              and s.warehouse.id = :warehouseId
              and s.quantity > 0
              and (:locationId is null or loc.id = :locationId)
              and (:lotId is null or lot.id = :lotId)
              and (:includeExpired = true or lot is null or lot.expiryDate is null or lot.expiryDate >= :today)
            order by lot.expiryDate asc nulls last, loc.code asc, s.id asc
            """)
    List<Stock> findFefoCandidates(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId,
                                   @Param("locationId") Long locationId, @Param("lotId") Long lotId,
                                   @Param("includeExpired") boolean includeExpired, @Param("today") LocalDate today);

    @Query("select coalesce(sum(s.quantity), 0) from Stock s where s.product.id = :productId and s.warehouse.id = :warehouseId")
    BigDecimal sumByProductAndWarehouse(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId);

    @Query(value = """
            select s from Stock s
            join fetch s.product p
            join fetch p.baseUom
            join fetch s.warehouse w
            join fetch s.location loc
            left join fetch s.lot lot
            where (:warehouseId is null or w.id = :warehouseId)
              and (:productId is null or p.id = :productId)
              and (:locationId is null or loc.id = :locationId)
              and (:onlyPositive = false or s.quantity > 0)
            """,
            countQuery = """
            select count(s) from Stock s
            where (:warehouseId is null or s.warehouse.id = :warehouseId)
              and (:productId is null or s.product.id = :productId)
              and (:locationId is null or s.location.id = :locationId)
              and (:onlyPositive = false or s.quantity > 0)
            """)
    Page<Stock> search(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId,
                       @Param("locationId") Long locationId, @Param("onlyPositive") boolean onlyPositive,
                       Pageable pageable);

    /** Stock que entra en un conteo cíclico, con filtros opcionales por pasillo y clase ABC (R-20). */
    @Query("""
            select s from Stock s
            join fetch s.product p
            join fetch s.location loc
            left join fetch s.lot lot
            where s.warehouse.id = :warehouseId
              and s.quantity > 0
              and loc.active = true
              and (:aisle is null or loc.aisle = :aisle)
              and (:abcClass is null or p.abcClass = :abcClass)
            order by loc.code, p.sku
            """)
    List<Stock> findForCycleCount(@Param("warehouseId") Long warehouseId, @Param("aisle") String aisle,
                                  @Param("abcClass") AbcClass abcClass);

    @Query("""
            select new io.github.diegofranciscog.inventory.repository.StockRepository$LowStockRow(
                r.product.id, r.product.sku, r.product.name, r.warehouse.id, r.warehouse.code, r.minQuantity,
                coalesce((select sum(s.quantity) from Stock s
                          where s.product.id = r.product.id and s.warehouse.id = r.warehouse.id), 0))
            from StockMinRule r
            order by r.product.sku, r.warehouse.code
            """)
    List<LowStockRow> findMinRuleStatus();

    @Query("""
            select new io.github.diegofranciscog.inventory.repository.StockRepository$ExpiringRow(
                lot.id, p.id, p.sku, p.name, lot.lotNumber, lot.expiryDate, w.id, w.code, sum(s.quantity))
            from Stock s join s.lot lot join s.product p join s.warehouse w
            where s.quantity > 0 and lot.expiryDate <= :limit
            group by lot.id, p.id, p.sku, p.name, lot.lotNumber, lot.expiryDate, w.id, w.code
            order by lot.expiryDate, p.sku, w.code
            """)
    List<ExpiringRow> findExpiring(@Param("limit") LocalDate limit);

    record LowStockRow(Long productId, String sku, String productName, Long warehouseId, String warehouseCode,
                       BigDecimal minQuantity, BigDecimal currentQuantity) {
    }

    record ExpiringRow(Long lotId, Long productId, String sku, String productName, String lotNumber,
                       LocalDate expiryDate, Long warehouseId, String warehouseCode, BigDecimal quantity) {
    }
}
