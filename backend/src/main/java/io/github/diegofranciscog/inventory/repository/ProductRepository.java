package io.github.diegofranciscog.inventory.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.diegofranciscog.inventory.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = {"category", "baseUom"})
    Optional<Product> findBySku(String sku);

    @EntityGraph(attributePaths = {"category", "baseUom"})
    Optional<Product> findByGtin(String gtin);

    @EntityGraph(attributePaths = {"category", "baseUom"})
    Optional<Product> findWithDetailsById(Long id);

    boolean existsBySku(String sku);

    boolean existsByGtin(String gtin);

    boolean existsByGtinAndIdNot(String gtin, Long id);

    long countByActiveTrue();

    /** Búsqueda por nombre, SKU o GTIN (parámetros enlazados; sin concatenar SQL). */
    @EntityGraph(attributePaths = {"category", "baseUom"})
    @Query(value = """
            select p from Product p
            where (:categoryId is null or p.category.id = :categoryId)
              and (:text is null
                   or lower(p.name) like lower(concat('%', :text, '%'))
                   or p.sku like upper(concat('%', :text, '%'))
                   or p.gtin like concat('%', :text, '%'))
            """,
            countQuery = """
            select count(p) from Product p
            where (:categoryId is null or p.category.id = :categoryId)
              and (:text is null
                   or lower(p.name) like lower(concat('%', :text, '%'))
                   or p.sku like upper(concat('%', :text, '%'))
                   or p.gtin like concat('%', :text, '%'))
            """)
    Page<Product> search(@Param("text") String text, @Param("categoryId") Long categoryId, Pageable pageable);
}
