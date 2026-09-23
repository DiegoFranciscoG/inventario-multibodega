package io.github.diegofranciscog.inventory.dto.movement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.github.diegofranciscog.inventory.dto.common.WarehouseRef;
import io.github.diegofranciscog.inventory.entity.Direction;
import io.github.diegofranciscog.inventory.entity.MovementType;

public final class MovementResponses {

    private MovementResponses() {
    }

    public record MovementResponse(
            Long id,
            String number,
            MovementType type,
            WarehouseRef warehouse,
            WarehouseRef targetWarehouse,
            String referenceTypeCode,
            String referenceTypeName,
            String referenceNumber,
            String reason,
            Instant occurredAt,
            String createdBy,
            Long cycleCountId,
            BigDecimal totalCost,
            List<MovementLineResponse> lines) {
    }

    public record MovementLineResponse(
            short lineNo,
            Long productId,
            String sku,
            String productName,
            String warehouseCode,
            String locationCode,
            String lotNumber,
            Direction direction,
            BigDecimal quantity,
            String uomCode,
            BigDecimal uomQuantity,
            BigDecimal unitCost,
            BigDecimal totalCost) {
    }

    public record MovementSummaryResponse(
            Long id,
            String number,
            MovementType type,
            String warehouseCode,
            String targetWarehouseCode,
            String referenceTypeCode,
            String referenceNumber,
            Instant occurredAt,
            String createdBy) {
    }
}
