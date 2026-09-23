package io.github.diegofranciscog.inventory.dto.storage;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.github.diegofranciscog.inventory.entity.LocationType;

/** Respuestas de almacenaje: bodegas, ubicaciones, lotes, stock y mínimos. */
public final class StorageResponses {

    private StorageResponses() {
    }

    public record WarehouseResponse(Long id, String code, String name, String city, String address, boolean active) {
    }

    public record LocationResponse(Long id, Long warehouseId, String warehouseCode, String code, String aisle,
                                   String rack, String level, LocationType type, boolean active, String qrPayload) {
    }

    public record LotResponse(Long id, Long productId, String lotNumber, LocalDate expiryDate, LocalDate productionDate,
                              boolean expired) {
    }

    public record StockResponse(
            Long id,
            Long productId,
            String sku,
            String productName,
            Long warehouseId,
            String warehouseCode,
            Long locationId,
            String locationCode,
            Long lotId,
            String lotNumber,
            LocalDate expiryDate,
            BigDecimal quantity,
            String uomCode) {
    }

    public record MinStockResponse(Long id, Long productId, String sku, Long warehouseId, String warehouseCode,
                                   BigDecimal minQuantity) {
    }
}
