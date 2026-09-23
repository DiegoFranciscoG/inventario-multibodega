package io.github.diegofranciscog.inventory.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.diegofranciscog.inventory.entity.AuditLog;
import io.github.diegofranciscog.inventory.repository.AuditLogRepository;
import io.github.diegofranciscog.inventory.security.CurrentUser;

/**
 * Bitácora de auditoría (R-21, OWASP A09). Las acciones de negocio se registran en la misma transacción: si la
 * operación se revierte, su registro también. Los eventos de seguridad (login fallido) usan una transacción propia.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, CurrentUser currentUser, Clock clock) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityType, Object entityId, Map<String, Object> details) {
        save(currentUser.emailOrSystem(), action, entityType, entityId, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSecurityEvent(String actorEmail, String action, Map<String, Object> details) {
        save(actorEmail, action, "Session", null, details);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(String entityType, String action, Pageable pageable) {
        return repository.search(blankToNull(entityType), blankToNull(action), pageable);
    }

    private void save(String actor, String action, String entityType, Object entityId, Map<String, Object> details) {
        repository.save(new AuditLog(Instant.now(clock), actor, action, entityType,
                entityId == null ? null : String.valueOf(entityId), details));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
