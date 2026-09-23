package io.github.diegofranciscog.inventory.entity;

/** Estados del conteo cíclico: OPEN → SUBMITTED → APPROVED | REJECTED, o CANCELLED antes de aprobar. */
public enum CycleCountStatus {
    OPEN,
    SUBMITTED,
    APPROVED,
    REJECTED,
    CANCELLED
}
