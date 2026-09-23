package io.github.diegofranciscog.inventory.mapper;

import java.math.BigDecimal;

import io.github.diegofranciscog.inventory.dto.catalog.CatalogResponses.CategoryResponse;
import io.github.diegofranciscog.inventory.dto.catalog.CatalogResponses.ConversionResponse;
import io.github.diegofranciscog.inventory.dto.catalog.CatalogResponses.DocumentTypeResponse;
import io.github.diegofranciscog.inventory.dto.catalog.CatalogResponses.ProductResponse;
import io.github.diegofranciscog.inventory.dto.catalog.CatalogResponses.UnitResponse;
import io.github.diegofranciscog.inventory.entity.Category;
import io.github.diegofranciscog.inventory.entity.DocumentType;
import io.github.diegofranciscog.inventory.entity.Product;
import io.github.diegofranciscog.inventory.entity.ProductCost;
import io.github.diegofranciscog.inventory.entity.ProductUomConversion;
import io.github.diegofranciscog.inventory.entity.UnitOfMeasure;

public final class CatalogMapper {

    private CatalogMapper() {
    }

    public static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getCode(), category.getName(), category.isActive());
    }

    public static UnitResponse toResponse(UnitOfMeasure unit) {
        return new UnitResponse(unit.getCode(), unit.getName());
    }

    public static DocumentTypeResponse toResponse(DocumentType type) {
        return new DocumentTypeResponse(type.getCode(), type.getName(), type.getSource());
    }

    public static ConversionResponse toResponse(ProductUomConversion conversion) {
        return new ConversionResponse(conversion.getUom().getCode(), conversion.getUom().getName(),
                conversion.getFactor(), conversion.getGtin());
    }

    /** {@code cost} puede ser {@code null} si el producto aún no tiene movimientos. */
    public static ProductResponse toResponse(Product product, ProductCost cost) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getGtin(),
                product.getName(),
                product.getDescription(),
                toResponse(product.getCategory()),
                product.getBaseUom().getCode(),
                product.getBaseUom().getName(),
                product.isLotControlled(),
                product.getAbcClass(),
                product.isActive(),
                product.getConversions().stream().map(CatalogMapper::toResponse).toList(),
                cost == null ? BigDecimal.ZERO : cost.getQuantityOnHand(),
                cost == null ? BigDecimal.ZERO : cost.getAverageCost(),
                cost == null ? BigDecimal.ZERO : cost.getTotalValue());
    }
}
