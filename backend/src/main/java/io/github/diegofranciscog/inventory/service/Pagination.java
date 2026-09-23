package io.github.diegofranciscog.inventory.service;

import io.github.diegofranciscog.inventory.config.AppProperties;

/** Límite de tamaño de página (OWASP API4: consumo de recursos sin restricción). */
final class Pagination {

    private static final int DEFAULT_SIZE = 20;

    private Pagination() {
    }

    static int clamp(int requested, AppProperties properties) {
        if (requested <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(requested, properties.inventory().maxPageSize());
    }
}
