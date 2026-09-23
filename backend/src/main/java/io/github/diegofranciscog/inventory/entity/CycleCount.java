package io.github.diegofranciscog.inventory.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

import io.github.diegofranciscog.inventory.exception.BusinessRuleException;
import io.github.diegofranciscog.inventory.exception.ForbiddenOperationException;

/**
 * Conteo cíclico de una bodega (R-20, R-21). Controla sus transiciones de estado y la segregación de funciones:
 * quien contó o envió el conteo no puede aprobarlo.
 */
@Entity
@Table(name = "cycle_count")
public class CycleCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private CycleCountStatus status = CycleCountStatus.OPEN;

    @Column(name = "aisle_filter", length = 3)
    private String aisleFilter;

    @Enumerated(EnumType.STRING)
    @Column(name = "abc_filter")
    private AbcClass abcFilter;

    @Column(length = 255)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id")
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_id")
    private AppUser submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private AppUser reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_notes", length = 500)
    private String reviewNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjustment_movement_id")
    private InventoryMovement adjustmentMovement;

    @Version
    private long version;

    @OneToMany(mappedBy = "cycleCount", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<CycleCountLine> lines = new ArrayList<>();

    protected CycleCount() {
    }

    public CycleCount(String number, Warehouse warehouse, String aisleFilter, AbcClass abcFilter, String notes,
                      AppUser createdBy, Instant createdAt) {
        this.number = number;
        this.warehouse = warehouse;
        this.aisleFilter = aisleFilter;
        this.abcFilter = abcFilter;
        this.notes = notes;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public CycleCountLine addLine(Location location, Product product, Lot lot) {
        requireStatus(CycleCountStatus.OPEN);
        boolean exists = lines.stream().anyMatch(line -> line.matches(location, product, lot));
        if (exists) {
            throw new BusinessRuleException("DUPLICATE_COUNT_LINE", "Ya existe una línea para ese producto, lote y ubicación");
        }
        CycleCountLine line = new CycleCountLine(this, location, product, lot);
        lines.add(line);
        return line;
    }

    public void submit(AppUser user, Instant at) {
        requireStatus(CycleCountStatus.OPEN);
        if (lines.isEmpty()) {
            throw new BusinessRuleException("EMPTY_COUNT", "El conteo no tiene líneas");
        }
        long pending = lines.stream().filter(line -> !line.isCounted()).count();
        if (pending > 0) {
            throw new BusinessRuleException("COUNT_INCOMPLETE", "Faltan %d líneas por contar".formatted(pending));
        }
        this.submittedBy = user;
        this.submittedAt = at;
        this.status = CycleCountStatus.SUBMITTED;
    }

    /** Aprueba el conteo; el ajuste ya fue registrado por el servicio y se enlaza aquí (R-21). */
    public void approve(AppUser reviewer, Instant at, String notes, InventoryMovement adjustment) {
        requireStatus(CycleCountStatus.SUBMITTED);
        requireSegregationOfDuties(reviewer);
        this.reviewedBy = reviewer;
        this.reviewedAt = at;
        this.reviewNotes = notes;
        this.adjustmentMovement = adjustment;
        this.status = CycleCountStatus.APPROVED;
    }

    public void reject(AppUser reviewer, Instant at, String notes) {
        requireStatus(CycleCountStatus.SUBMITTED);
        requireSegregationOfDuties(reviewer);
        this.reviewedBy = reviewer;
        this.reviewedAt = at;
        this.reviewNotes = notes;
        this.status = CycleCountStatus.REJECTED;
    }

    public void cancel() {
        if (status != CycleCountStatus.OPEN && status != CycleCountStatus.SUBMITTED) {
            throw new BusinessRuleException("INVALID_COUNT_STATUS", "Solo se puede cancelar un conteo abierto o enviado");
        }
        this.status = CycleCountStatus.CANCELLED;
    }

    /** Segregación de funciones (COSO principio 10): quien envió o contó no puede aprobar. */
    public void requireSegregationOfDuties(AppUser reviewer) {
        boolean submitted = submittedBy != null && Objects.equals(submittedBy.getId(), reviewer.getId());
        boolean counted = lines.stream().anyMatch(line -> line.wasCountedBy(reviewer));
        if (submitted || counted) {
            throw new ForbiddenOperationException("SEGREGATION_OF_DUTIES",
                    "Quien contó o envió el conteo no puede aprobarlo ni rechazarlo");
        }
    }

    public void requireStatus(CycleCountStatus expected) {
        if (status != expected) {
            throw new BusinessRuleException("INVALID_COUNT_STATUS",
                    "El conteo está en estado %s y se esperaba %s".formatted(status, expected));
        }
    }

    public boolean hasDifferences() {
        return lines.stream().anyMatch(CycleCountLine::hasDifference);
    }

    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public CycleCountStatus getStatus() {
        return status;
    }

    public String getAisleFilter() {
        return aisleFilter;
    }

    public AbcClass getAbcFilter() {
        return abcFilter;
    }

    public String getNotes() {
        return notes;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public AppUser getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public AppUser getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public InventoryMovement getAdjustmentMovement() {
        return adjustmentMovement;
    }

    public List<CycleCountLine> getLines() {
        return lines;
    }
}
