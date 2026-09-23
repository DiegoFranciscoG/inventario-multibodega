package io.github.diegofranciscog.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/** Unidad de medida con código UNECE Rec. 20 (R-23). Catálogo cargado por migración. */
@Entity
@Immutable
@Table(name = "unit_of_measure")
public class UnitOfMeasure {

    @Id
    @Column(length = 3)
    private String code;

    @Column(nullable = false, length = 40)
    private String name;

    protected UnitOfMeasure() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
