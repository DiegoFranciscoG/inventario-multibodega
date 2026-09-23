package io.github.diegofranciscog.inventory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.diegofranciscog.inventory.entity.StockMinRule;

public interface StockMinRuleRepository extends JpaRepository<StockMinRule, Long> {

    Optional<StockMinRule> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    @Query("select r from StockMinRule r join fetch r.warehouse join fetch r.product where r.product.id = :productId")
    List<StockMinRule> findByProduct(@Param("productId") Long productId);
}
