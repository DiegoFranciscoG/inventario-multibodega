package io.github.diegofranciscog.inventory.dto.catalog;

import java.math.BigDecimal;
import java.util.List;

import io.github.diegofranciscog.inventory.entity.AbcClass;

/** Respuestas del catálogo (categorías, unidades, tipos de documento y productos). */
public final class CatalogResponses {

    private CatalogResponses() {
    }

    public record CategoryResponse(Long id, String code, String name, boolean active) {
    }

    public record UnitResponse(String code, String name) {
    }

    public record DocumentTypeResponse(String code, String name, String source) {
    }

    public record ConversionResponse(String uomCode, String uomName, BigDecimal factor, String gtin) {
    }

    public record ProductResponse(
            Long id,
            String sku,
            String gtin,
            String name,
            String description,
            CategoryResponse category,
            String baseUomCode,
            String baseUomName,
            boolean lotControlled,
            AbcClass abcClass,
            boolean active,
            List<ConversionResponse> conversions,
            BigDecimal quantityOnHand,
            BigDecimal averageCost,
            BigDecimal totalValue) {
    }
}
