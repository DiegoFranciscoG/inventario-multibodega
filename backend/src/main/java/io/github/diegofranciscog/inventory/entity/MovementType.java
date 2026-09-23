package io.github.diegofranciscog.inventory.entity;

/** Tipos de movimiento de inventario y el prefijo de su numeración. */
public enum MovementType {
    RECEIPT("ING"),
    ISSUE("EGR"),
    TRANSFER("TRF"),
    ADJUSTMENT("AJU");

    private final String prefix;

    MovementType(String prefix) {
        this.prefix = prefix;
    }

    public String formatNumber(long sequence) {
        return "%s-%06d".formatted(prefix, sequence);
    }
}
