package io.github.diegofranciscog.inventory.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * Renglón del kárdex (R-06): cantidad, costo y saldos del producto después de aplicar el renglón. Solo inserción.
 */
@Entity
@Immutable
@Table(name = "movement_line")
public class MovementLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movement_id")
    private InventoryMovement movement;

    @Column(name = "line_no", nullable = false)
    private short lineNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private Lot lot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Direction direction;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "uom_code", nullable = false, length = 3)
    private String uomCode;

    @Column(name = "uom_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal uomQuantity;

    @Column(name = "unit_cost", nullable = false, precision = 20, scale = 6)
    private BigDecimal unitCost;

    @Column(name = "total_cost", nullable = false, precision = 20, scale = 4)
    private BigDecimal totalCost;

    @Column(name = "balance_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal balanceQuantity;

    @Column(name = "balance_value", nullable = false, precision = 20, scale = 4)
    private BigDecimal balanceValue;

    @Column(name = "average_cost", nullable = false, precision = 20, scale = 6)
    private BigDecimal averageCost;

    @Column(name = "warehouse_balance_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal warehouseBalanceQuantity;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected MovementLine() {
    }

    public MovementLine(InventoryMovement movement, Stock stock, Direction direction, BigDecimal quantity,
                        String uomCode, BigDecimal uomQuantity, CostPosting posting, BigDecimal warehouseBalanceQuantity) {
        this.movement = movement;
        this.lineNo = movement.nextLineNumber();
        this.product = stock.getProduct();
        this.warehouse = stock.getWarehouse();
        this.location = stock.getLocation();
        this.lot = stock.getLot();
        this.direction = direction;
        this.quantity = quantity;
        this.uomCode = uomCode;
        this.uomQuantity = uomQuantity;
        this.unitCost = posting.unitCost();
        this.totalCost = posting.totalCost();
        this.balanceQuantity = posting.balanceQuantity();
        this.balanceValue = posting.balanceValue();
        this.averageCost = posting.averageCost();
        this.warehouseBalanceQuantity = warehouseBalanceQuantity;
        this.occurredAt = movement.getOccurredAt();
        movement.addLine(this);
    }

    public Long getId() {
        return id;
    }

    public InventoryMovement getMovement() {
        return movement;
    }

    public short getLineNo() {
        return lineNo;
    }

    public Product getProduct() {
        return product;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public Location getLocation() {
        return location;
    }

    public Lot getLot() {
        return lot;
    }

    public Direction getDirection() {
        return direction;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getUomCode() {
        return uomCode;
    }

    public BigDecimal getUomQuantity() {
        return uomQuantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public BigDecimal getBalanceQuantity() {
        return balanceQuantity;
    }

    public BigDecimal getBalanceValue() {
        return balanceValue;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public BigDecimal getWarehouseBalanceQuantity() {
        return warehouseBalanceQuantity;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
