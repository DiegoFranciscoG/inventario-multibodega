package io.github.diegofranciscog.inventory.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.github.diegofranciscog.inventory.exception.BusinessRuleException;

/**
 * Costo promedio ponderado móvil de un producto para toda la empresa (R-01 a R-04, NIC 2 párr. 25–27).
 * <p>
 * Cada movimiento de un producto actualiza esta fila; su {@code @Version} serializa los movimientos del mismo producto
 * y garantiza que los saldos del kárdex se calculen sobre el último estado confirmado (R-10).
 */
@Entity
@Table(name = "product_cost")
public class ProductCost {

    public static final int QUANTITY_SCALE = 4;
    public static final int VALUE_SCALE = 4;
    public static final int COST_SCALE = 6;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "quantity_on_hand", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantityOnHand = BigDecimal.ZERO;

    @Column(name = "total_value", nullable = false, precision = 20, scale = 4)
    private BigDecimal totalValue = BigDecimal.ZERO;

    @Column(name = "average_cost", nullable = false, precision = 20, scale = 6)
    private BigDecimal averageCost = BigDecimal.ZERO;

    @Column(name = "last_movement_at")
    private Instant lastMovementAt;

    @Version
    private long version;

    protected ProductCost() {
    }

    public ProductCost(Long productId) {
        this.productId = productId;
    }

    /** Ingreso: suma cantidad y valor y recalcula el promedio (R-02). */
    public CostPosting receive(BigDecimal quantity, BigDecimal unitCost, Instant at) {
        requirePositive(quantity);
        if (unitCost == null || unitCost.signum() < 0) {
            throw new BusinessRuleException("INVALID_COST", "El costo unitario no puede ser negativo");
        }
        BigDecimal cost = unitCost.setScale(COST_SCALE, ROUNDING);
        BigDecimal lineValue = quantity.multiply(cost).setScale(VALUE_SCALE, ROUNDING);
        quantityOnHand = quantityOnHand.add(quantity).setScale(QUANTITY_SCALE, ROUNDING);
        totalValue = totalValue.add(lineValue).setScale(VALUE_SCALE, ROUNDING);
        averageCost = totalValue.divide(quantityOnHand, COST_SCALE, ROUNDING);
        touch(at);
        return posting(cost, lineValue);
    }

    /** Egreso: sale al costo promedio vigente; el promedio no cambia (R-04). */
    public CostPosting issue(BigDecimal quantity, Instant at) {
        requirePositive(quantity);
        if (quantityOnHand.compareTo(quantity) < 0) {
            throw new BusinessRuleException("VALUATION_MISMATCH",
                    "La cantidad valorizada del producto es menor que la cantidad a egresar");
        }
        BigDecimal remaining = quantityOnHand.subtract(quantity);
        BigDecimal lineValue;
        if (remaining.signum() == 0) {
            // Al vaciar el producto sale todo el valor restante: no quedan residuos de redondeo.
            lineValue = totalValue;
        } else {
            lineValue = quantity.multiply(averageCost).setScale(VALUE_SCALE, ROUNDING).min(totalValue);
        }
        quantityOnHand = remaining.setScale(QUANTITY_SCALE, ROUNDING);
        totalValue = totalValue.subtract(lineValue).setScale(VALUE_SCALE, ROUNDING);
        touch(at);
        return posting(averageCost, lineValue);
    }

    /**
     * Entrada de una transferencia: vuelve a sumar exactamente el valor que salió de la bodega de origen, sin recalcular
     * el promedio (R-03: mover entre bodegas propias no cambia el costo).
     */
    public CostPosting transferIn(BigDecimal quantity, BigDecimal unitCost, BigDecimal lineValue, Instant at) {
        requirePositive(quantity);
        quantityOnHand = quantityOnHand.add(quantity).setScale(QUANTITY_SCALE, ROUNDING);
        totalValue = totalValue.add(lineValue).setScale(VALUE_SCALE, ROUNDING);
        touch(at);
        return posting(unitCost, lineValue);
    }

    private void touch(Instant at) {
        lastMovementAt = at;
    }

    private CostPosting posting(BigDecimal unitCost, BigDecimal lineValue) {
        return new CostPosting(unitCost, lineValue, quantityOnHand, totalValue, averageCost);
    }

    private static void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessRuleException("INVALID_QUANTITY", "La cantidad debe ser mayor que cero");
        }
    }

    public Long getProductId() {
        return productId;
    }

    public BigDecimal getQuantityOnHand() {
        return quantityOnHand;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public Instant getLastMovementAt() {
        return lastMovementAt;
    }

    public long getVersion() {
        return version;
    }
}
