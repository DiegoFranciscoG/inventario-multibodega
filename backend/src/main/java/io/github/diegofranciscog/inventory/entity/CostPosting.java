package io.github.diegofranciscog.inventory.entity;

import java.math.BigDecimal;

/**
 * Resultado de aplicar un movimiento al costo promedio: costo del renglón y saldos del producto después del renglón.
 */
public record CostPosting(
        BigDecimal unitCost,
        BigDecimal totalCost,
        BigDecimal balanceQuantity,
        BigDecimal balanceValue,
        BigDecimal averageCost) {
}
