package io.github.diegofranciscog.inventory.entity;

/** Roles del sistema (tabla catálogo {@code role}). */
public enum Role {
    ADMIN,
    SUPERVISOR,
    OPERATOR,
    AUDITOR;

    /** Los operadores solo pueden operar en las bodegas que tienen asignadas (R-25). */
    public boolean isRestrictedToAssignedWarehouses() {
        return this == OPERATOR;
    }
}
