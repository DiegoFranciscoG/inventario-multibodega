package io.github.diegofranciscog.inventory.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.github.diegofranciscog.inventory.exception.BusinessRuleException;

/**
 * Línea de conteo (R-20). La cantidad del sistema se fija en el momento de contar y el contador no la ve (conteo ciego);
 * diferencia = contado − sistema.
 */
@Entity
@Table(name = "cycle_count_line")
public class CycleCountLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_count_id")
    private CycleCount cycleCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private Lot lot;

    @Column(name = "counted_quantity", precision = 18, scale = 4)
    private BigDecimal countedQuantity;

    @Column(name = "system_quantity", precision = 18, scale = 4)
    private BigDecimal systemQuantity;

    @Column(precision = 18, scale = 4)
    private BigDecimal difference;

    @Column(name = "unit_cost", precision = 20, scale = 6)
    private BigDecimal unitCost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counted_by_id")
    private AppUser countedBy;

    @Column(name = "counted_at")
    private Instant countedAt;

    @Column(length = 255)
    private String note;

    @Version
    private long version;

    protected CycleCountLine() {
    }

    CycleCountLine(CycleCount cycleCount, Location location, Product product, Lot lot) {
        this.cycleCount = cycleCount;
        this.location = location;
        this.product = product;
        this.lot = lot;
    }

    public void registerCount(BigDecimal counted, BigDecimal systemQuantity, BigDecimal unitCost, AppUser user,
                              Instant at, String note) {
        cycleCount.requireStatus(CycleCountStatus.OPEN);
        if (counted == null || counted.signum() < 0) {
            throw new BusinessRuleException("INVALID_QUANTITY", "La cantidad contada no puede ser negativa");
        }
        this.countedQuantity = counted.setScale(ProductCost.QUANTITY_SCALE, RoundingMode.HALF_UP);
        this.systemQuantity = systemQuantity.setScale(ProductCost.QUANTITY_SCALE, RoundingMode.HALF_UP);
        this.difference = this.countedQuantity.subtract(this.systemQuantity);
        this.unitCost = unitCost;
        this.countedBy = user;
        this.countedAt = at;
        this.note = note;
    }

    public boolean isCounted() {
        return countedQuantity != null;
    }

    public boolean hasDifference() {
        return difference != null && difference.signum() != 0;
    }

    /** Valor de la diferencia al costo promedio del momento del conteo. */
    public BigDecimal differenceValue() {
        if (difference == null || unitCost == null) {
            return BigDecimal.ZERO;
        }
        return difference.multiply(unitCost).setScale(ProductCost.VALUE_SCALE, RoundingMode.HALF_UP);
    }

    boolean matches(Location otherLocation, Product otherProduct, Lot otherLot) {
        return Objects.equals(location.getId(), otherLocation.getId())
                && Objects.equals(product.getId(), otherProduct.getId())
                && Objects.equals(lot == null ? null : lot.getId(), otherLot == null ? null : otherLot.getId());
    }

    boolean wasCountedBy(AppUser user) {
        return countedBy != null && Objects.equals(countedBy.getId(), user.getId());
    }

    public Long getId() {
        return id;
    }

    public CycleCount getCycleCount() {
        return cycleCount;
    }

    public Location getLocation() {
        return location;
    }

    public Product getProduct() {
        return product;
    }

    public Lot getLot() {
        return lot;
    }

    public BigDecimal getCountedQuantity() {
        return countedQuantity;
    }

    public BigDecimal getSystemQuantity() {
        return systemQuantity;
    }

    public BigDecimal getDifference() {
        return difference;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public AppUser getCountedBy() {
        return countedBy;
    }

    public Instant getCountedAt() {
        return countedAt;
    }

    public String getNote() {
        return note;
    }
}
