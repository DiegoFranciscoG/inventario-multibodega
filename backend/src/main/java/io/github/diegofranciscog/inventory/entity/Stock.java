package io.github.diegofranciscog.inventory.entity;

import java.math.BigDecimal;
import java.time.Instant;

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

import io.github.diegofranciscog.inventory.exception.InsufficientStockException;

/**
 * Existencia de un producto en una ubicación y lote. {@code @Version} detecta escrituras concurrentes (R-10) y la
 * restricción {@code CHECK (quantity >= 0)} de la base de datos es la última barrera contra el stock negativo (R-07).
 */
@Entity
@Table(name = "stock")
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Version
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Stock() {
    }

    public Stock(Product product, Warehouse warehouse, Location location, Lot lot) {
        this.product = product;
        this.warehouse = warehouse;
        this.location = location;
        this.lot = lot;
    }

    public void increase(BigDecimal amount, Instant at) {
        requirePositive(amount);
        quantity = quantity.add(amount);
        updatedAt = at;
    }

    public void decrease(BigDecimal amount, Instant at) {
        requirePositive(amount);
        if (quantity.compareTo(amount) < 0) {
            throw new InsufficientStockException(product.getSku(), location.getCode(), quantity, amount);
        }
        quantity = quantity.subtract(amount);
        updatedAt = at;
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
    }

    public Long getId() {
        return id;
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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
