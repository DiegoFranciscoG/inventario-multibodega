package io.github.diegofranciscog.inventory.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Stock mínimo de un producto en una bodega (R-19). */
@Entity
@Table(name = "stock_min_rule")
public class StockMinRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Column(name = "min_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal minQuantity;

    protected StockMinRule() {
    }

    public StockMinRule(Product product, Warehouse warehouse, BigDecimal minQuantity) {
        this.product = product;
        this.warehouse = warehouse;
        this.minQuantity = minQuantity;
    }

    public void changeMinimum(BigDecimal minQuantity) {
        this.minQuantity = minQuantity;
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

    public BigDecimal getMinQuantity() {
        return minQuantity;
    }
}
