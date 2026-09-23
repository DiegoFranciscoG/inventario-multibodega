package io.github.diegofranciscog.inventory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.diegofranciscog.inventory.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            select a from AuditLog a
            where (:entityType is null or a.entityType = :entityType)
              and (:action is null or a.action = :action)
            """)
    Page<AuditLog> search(@Param("entityType") String entityType, @Param("action") String action, Pageable pageable);
}
