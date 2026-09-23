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

/** Empaque de un producto: cuántas unidades base contiene y su GTIN-14 opcional (R-12). */
@Entity
@Table(name = "product_uom_conversion")
public class ProductUomConversion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "uom_code")
    private UnitOfMeasure uom;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal factor;

    @Column(length = 14)
    private String gtin;

    protected ProductUomConversion() {
    }

    ProductUomConversion(Product product, UnitOfMeasure uom, BigDecimal factor, String gtin) {
        this.product = product;
        this.uom = uom;
        this.factor = factor;
        this.gtin = gtin;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public UnitOfMeasure getUom() {
        return uom;
    }

    public BigDecimal getFactor() {
        return factor;
    }

    public String getGtin() {
        return gtin;
    }
}
