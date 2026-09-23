package io.github.diegofranciscog.inventory.dto.kardex;

import java.math.BigDecimal;
import java.time.Instant;

import io.github.diegofranciscog.inventory.dto.common.PageResponse;
import io.github.diegofranciscog.inventory.dto.common.WarehouseRef;
import io.github.diegofranciscog.inventory.entity.Direction;
import io.github.diegofranciscog.inventory.entity.MovementType;

public final class KardexResponses {

    private KardexResponses() {
    }

    public record KardexEntry(
            Long lineId,
            Instant occurredAt,
            String movementNumber,
            MovementType movementType,
            String referenceTypeCode,
            String referenceNumber,
            String warehouseCode,
            String locationCode,
            String lotNumber,
            Direction direction,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal totalCost,
            BigDecimal balanceQuantity,
            BigDecimal balanceValue,
            BigDecimal averageCost,
            BigDecimal warehouseBalanceQuantity) {
    }

    /** Totales del rango filtrado completo (no solo de la página). */
    public record KardexTotals(BigDecimal inQuantity, BigDecimal inValue, BigDecimal outQuantity, BigDecimal outValue) {
    }

    public record KardexResponse(
            Long productId,
            String sku,
            String productName,
            String uomCode,
            WarehouseRef warehouse,
            String costMethod,
            KardexTotals totals,
            PageResponse<KardexEntry> entries) {
    }
}
