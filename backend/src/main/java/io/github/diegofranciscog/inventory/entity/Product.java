package io.github.diegofranciscog.inventory.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String sku;

    /** GTIN normalizado a 14 dígitos (R-11). */
    @Column(length = 14)
    private String gtin;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_uom_code")
    private UnitOfMeasure baseUom;

    @Column(name = "lot_controlled", nullable = false)
    private boolean lotControlled;

    @Enumerated(EnumType.STRING)
    @Column(name = "abc_class", nullable = false)
    private AbcClass abcClass = AbcClass.C;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Version
    private long version;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("factor ASC")
    private List<ProductUomConversion> conversions = new ArrayList<>();

    protected Product() {
    }

    public Product(String sku, String gtin, String name, String description, Category category,
                   UnitOfMeasure baseUom, boolean lotControlled, AbcClass abcClass) {
        this.sku = sku;
        this.gtin = gtin;
        this.name = name;
        this.description = description;
        this.category = category;
        this.baseUom = baseUom;
        this.lotControlled = lotControlled;
        this.abcClass = abcClass;
    }

    public void update(String gtin, String name, String description, Category category, AbcClass abcClass, boolean active) {
        this.gtin = gtin;
        this.name = name;
        this.description = description;
        this.category = category;
        this.abcClass = abcClass;
        this.active = active;
    }

    public ProductUomConversion addConversion(UnitOfMeasure uom, BigDecimal factor, String gtin) {
        ProductUomConversion conversion = new ProductUomConversion(this, uom, factor, gtin);
        conversions.add(conversion);
        return conversion;
    }

    public void removeConversion(String uomCode) {
        conversions.removeIf(conversion -> conversion.getUom().getCode().equals(uomCode));
    }

    /** Factor de conversión a la unidad base; 1 para la propia unidad base (R-12). */
    public Optional<BigDecimal> factorFor(String uomCode) {
        if (uomCode == null || baseUom.getCode().equals(uomCode)) {
            return Optional.of(BigDecimal.ONE);
        }
        return conversions.stream()
                .filter(conversion -> conversion.getUom().getCode().equals(uomCode))
                .map(ProductUomConversion::getFactor)
                .findFirst();
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getGtin() {
        return gtin;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public UnitOfMeasure getBaseUom() {
        return baseUom;
    }

    public boolean isLotControlled() {
        return lotControlled;
    }

    public AbcClass getAbcClass() {
        return abcClass;
    }

    public boolean isActive() {
        return active;
    }

    public List<ProductUomConversion> getConversions() {
        return conversions;
    }
}
