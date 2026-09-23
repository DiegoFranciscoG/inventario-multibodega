package io.github.diegofranciscog.inventory.dto.storage;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import io.github.diegofranciscog.inventory.entity.LocationType;

/** Solicitudes de almacenaje: bodegas, ubicaciones y stock mínimo. */
public final class StorageRequests {

    private StorageRequests() {
    }

    public record WarehouseRequest(
            @NotBlank @Pattern(regexp = "^[A-Z0-9-]{2,10}$", message = "Código: 2 a 10 caracteres A-Z, 0-9 o -") String code,
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 100) String name,
            @Size(max = 60) String city,
            @Size(max = 200) String address,
            Boolean active) {
    }

    public record LocationRequest(
            @NotBlank @Pattern(regexp = "^[A-Z0-9]{1,3}$", message = "Pasillo: 1 a 3 caracteres A-Z o 0-9") String aisle,
            @NotBlank @Pattern(regexp = "^[A-Z0-9]{1,3}$", message = "Estante: 1 a 3 caracteres A-Z o 0-9") String rack,
            @NotBlank @Pattern(regexp = "^[A-Z0-9]{1,3}$", message = "Nivel: 1 a 3 caracteres A-Z o 0-9") String level,
            LocationType type,
            Boolean active) {
    }

    public record MinStockRequest(
            @NotNull(message = "La bodega es obligatoria") Long warehouseId,
            @NotNull @PositiveOrZero(message = "El mínimo no puede ser negativo") @Digits(integer = 14, fraction = 4)
            BigDecimal minQuantity) {
    }
}
