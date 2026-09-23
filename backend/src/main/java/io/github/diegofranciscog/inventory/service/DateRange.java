package io.github.diegofranciscog.inventory.service;

import java.time.Instant;

/**
 * Límites para filtros de fecha abiertos. Se usan en lugar de parámetros nulos porque PostgreSQL no puede inferir el
 * tipo de un {@code timestamptz} nulo en expresiones como {@code :from is null}.
 */
final class DateRange {

    static final Instant MIN = Instant.parse("1900-01-01T00:00:00Z");
    static final Instant MAX = Instant.parse("3000-01-01T00:00:00Z");

    private DateRange() {
    }
}
