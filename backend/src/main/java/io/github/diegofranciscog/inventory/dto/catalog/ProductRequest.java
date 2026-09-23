package io.github.diegofranciscog.inventory.dto.catalog;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import io.github.diegofranciscog.inventory.entity.AbcClass;
import io.github.diegofranciscog.inventory.validation.ValidGtin;

public record ProductRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9._-]{1,39}$",
                message = "SKU: 2 a 40 caracteres en mayúsculas, números, punto, guion o guion bajo") String sku,
        @ValidGtin String gtin,
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String name,
        @Size(max = 500) String description,
        @NotNull(message = "La categoría es obligatoria") Long categoryId,
        @NotBlank(message = "La unidad base es obligatoria") String baseUomCode,
        Boolean lotControlled,
        AbcClass abcClass,
        Boolean active,
        @Valid @Size(max = 10) List<Conversion> conversions) {

    /** Empaque del producto: factor = unidades base por empaque (R-12). */
    public record Conversion(
            @NotBlank(message = "La unidad es obligatoria") String uomCode,
            @NotNull @Positive(message = "El factor debe ser mayor que cero") @DecimalMax("1000000")
            @Digits(integer = 12, fraction = 6) BigDecimal factor,
            @ValidGtin String gtin) {
    }
}
