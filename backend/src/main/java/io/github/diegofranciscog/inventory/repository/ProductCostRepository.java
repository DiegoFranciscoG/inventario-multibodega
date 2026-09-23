package io.github.diegofranciscog.inventory.repository;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.diegofranciscog.inventory.entity.ProductCost;

public interface ProductCostRepository extends JpaRepository<ProductCost, Long> {

    @Modifying(flushAutomatically = true)
    @Query(value = "insert into product_cost (product_id) values (:productId) on conflict do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("productId") Long productId);

    @Query("select coalesce(sum(c.totalValue), 0) from ProductCost c")
    BigDecimal totalInventoryValue();
}
