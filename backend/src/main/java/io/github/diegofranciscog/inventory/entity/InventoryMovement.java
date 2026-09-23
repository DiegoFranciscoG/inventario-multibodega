package io.github.diegofranciscog.inventory.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

import org.hibernate.annotations.Immutable;

/**
 * Cabecera de un movimiento de inventario. Es inmutable (R-06): la base de datos rechaza UPDATE y DELETE con un trigger.
 */
@Entity
@Immutable
@Table(name = "inventory_movement")
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private MovementType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_warehouse_id")
    private Warehouse targetWarehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reference_type_code")
    private DocumentType referenceType;

    @Column(name = "reference_number", nullable = false, length = 40)
    private String referenceNumber;

    @Column(length = 255)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id")
    private AppUser createdBy;

    @Column(name = "cycle_count_id")
    private Long cycleCountId;

    @OneToMany(mappedBy = "movement")
    @OrderBy("lineNo ASC")
    private List<MovementLine> lines = new ArrayList<>();

    protected InventoryMovement() {
    }

    public InventoryMovement(String number, MovementType type, Warehouse warehouse, Warehouse targetWarehouse,
                             DocumentType referenceType, String referenceNumber, String reason, Instant occurredAt,
                             AppUser createdBy, Long cycleCountId) {
        this.number = number;
        this.type = type;
        this.warehouse = warehouse;
        this.targetWarehouse = targetWarehouse;
        this.referenceType = referenceType;
        this.referenceNumber = referenceNumber;
        this.reason = reason;
        this.occurredAt = occurredAt;
        this.createdBy = createdBy;
        this.cycleCountId = cycleCountId;
    }

    public void addLine(MovementLine line) {
        lines.add(line);
    }

    public short nextLineNumber() {
        return (short) (lines.size() + 1);
    }

    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public MovementType getType() {
        return type;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public Warehouse getTargetWarehouse() {
        return targetWarehouse;
    }

    public DocumentType getReferenceType() {
        return referenceType;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public Long getCycleCountId() {
        return cycleCountId;
    }

    public List<MovementLine> getLines() {
        return lines;
    }
}
