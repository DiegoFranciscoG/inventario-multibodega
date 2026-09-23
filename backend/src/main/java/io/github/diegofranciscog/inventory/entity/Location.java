package io.github.diegofranciscog.inventory.entity;

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
import jakarta.persistence.Version;

/** Ubicación física: bodega → pasillo → estante → nivel (R-17). */
@Entity
@Table(name = "location")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 3)
    private String aisle;

    @Column(nullable = false, length = 3)
    private String rack;

    @Column(nullable = false, length = 3)
    private String level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private LocationType type;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private long version;

    protected Location() {
    }

    public Location(Warehouse warehouse, String aisle, String rack, String level, LocationType type) {
        this.warehouse = warehouse;
        this.aisle = aisle;
        this.rack = rack;
        this.level = level;
        this.type = type;
        this.code = buildCode(aisle, rack, level);
    }

    /** Código legible y único dentro de la bodega, p. ej. {@code P01-E02-N3}. */
    public static String buildCode(String aisle, String rack, String level) {
        return "P" + aisle + "-E" + rack + "-N" + level;
    }

    /** Contenido del QR interno de la ubicación (R-14). */
    public String qrPayload() {
        return "LOC:" + warehouse.getCode() + ":" + code;
    }

    public void update(LocationType type, boolean active) {
        this.type = type;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public String getCode() {
        return code;
    }

    public String getAisle() {
        return aisle;
    }

    public String getRack() {
        return rack;
    }

    public String getLevel() {
        return level;
    }

    public LocationType getType() {
        return type;
    }

    public boolean isActive() {
        return active;
    }
}
