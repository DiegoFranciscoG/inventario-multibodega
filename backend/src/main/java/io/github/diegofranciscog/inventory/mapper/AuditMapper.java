package io.github.diegofranciscog.inventory.mapper;

import io.github.diegofranciscog.inventory.dto.report.ReportResponses.AuditLogResponse;
import io.github.diegofranciscog.inventory.entity.AuditLog;

public final class AuditMapper {

    private AuditMapper() {
    }

    public static AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getOccurredAt(), log.getActorEmail(), log.getAction(),
                log.getEntityType(), log.getEntityId(), log.getDetails());
    }
}
