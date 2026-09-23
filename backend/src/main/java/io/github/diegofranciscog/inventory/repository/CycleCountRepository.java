package io.github.diegofranciscog.inventory.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.diegofranciscog.inventory.entity.CycleCount;
import io.github.diegofranciscog.inventory.entity.CycleCountStatus;

public interface CycleCountRepository extends JpaRepository<CycleCount, Long> {

    @Query(value = "select nextval('cycle_count_number_seq')", nativeQuery = true)
    long nextNumber();

    @EntityGraph(attributePaths = {"warehouse", "createdBy", "submittedBy", "reviewedBy", "adjustmentMovement",
            "lines", "lines.location", "lines.product", "lines.lot", "lines.countedBy"})
    Optional<CycleCount> findWithLinesById(Long id);

    @Query(value = """
            select c from CycleCount c
            join fetch c.warehouse w
            join fetch c.createdBy
            left join fetch c.submittedBy
            left join fetch c.reviewedBy
            where (:warehouseId is null or w.id = :warehouseId)
              and (:status is null or c.status = :status)
            """,
            countQuery = """
            select count(c) from CycleCount c
            where (:warehouseId is null or c.warehouse.id = :warehouseId)
              and (:status is null or c.status = :status)
            """)
    Page<CycleCount> search(@Param("warehouseId") Long warehouseId, @Param("status") CycleCountStatus status,
                            Pageable pageable);

    long countByStatus(CycleCountStatus status);
}
