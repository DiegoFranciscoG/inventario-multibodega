package io.github.diegofranciscog.inventory.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.diegofranciscog.inventory.entity.ProductUomConversion;

public interface ProductUomConversionRepository extends JpaRepository<ProductUomConversion, Long> {

    @Query("select c from ProductUomConversion c join fetch c.product p join fetch c.uom where c.gtin = :gtin")
    Optional<ProductUomConversion> findByGtin(@Param("gtin") String gtin);

    boolean existsByGtin(String gtin);
}
