package io.github.diegofranciscog.inventory.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Lote de un producto: número (AI 10) y vencimiento (AI 17) — R-13. */
@Entity
@Table(name = "lot")
public class Lot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "lot_number", nullable = false, length = 20)
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "production_date")
    private LocalDate productionDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Lot() {
    }

    public Lot(Product product, String lotNumber, LocalDate expiryDate, LocalDate productionDate) {
        this.product = product;
        this.lotNumber = lotNumber;
        this.expiryDate = expiryDate;
        this.productionDate = productionDate;
    }

    /** Un lote está vencido si su fecha de vencimiento es anterior a {@code today}. */
    public boolean isExpiredOn(LocalDate today) {
        return expiryDate != null && expiryDate.isBefore(today);
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getLotNumber() {
        return lotNumber;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public LocalDate getProductionDate() {
        return productionDate;
    }
}
