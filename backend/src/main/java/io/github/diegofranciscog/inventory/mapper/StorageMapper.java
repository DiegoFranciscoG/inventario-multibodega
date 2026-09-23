package io.github.diegofranciscog.inventory.mapper;

import java.time.LocalDate;

import io.github.diegofranciscog.inventory.dto.storage.StorageResponses.LocationResponse;
import io.github.diegofranciscog.inventory.dto.storage.StorageResponses.LotResponse;
import io.github.diegofranciscog.inventory.dto.storage.StorageResponses.MinStockResponse;
import io.github.diegofranciscog.inventory.dto.storage.StorageResponses.StockResponse;
import io.github.diegofranciscog.inventory.dto.storage.StorageResponses.WarehouseResponse;
import io.github.diegofranciscog.inventory.entity.Location;
import io.github.diegofranciscog.inventory.entity.Lot;
import io.github.diegofranciscog.inventory.entity.Stock;
import io.github.diegofranciscog.inventory.entity.StockMinRule;
import io.github.diegofranciscog.inventory.entity.Warehouse;

public final class StorageMapper {

    private StorageMapper() {
    }

    public static WarehouseResponse toResponse(Warehouse warehouse) {
        return new WarehouseResponse(warehouse.getId(), warehouse.getCode(), warehouse.getName(), warehouse.getCity(),
                warehouse.getAddress(), warehouse.isActive());
    }

    public static LocationResponse toResponse(Location location) {
        return new LocationResponse(location.getId(), location.getWarehouse().getId(), location.getWarehouse().getCode(),
                location.getCode(), location.getAisle(), location.getRack(), location.getLevel(), location.getType(),
                location.isActive(), location.qrPayload());
    }

    public static LotResponse toResponse(Lot lot, LocalDate today) {
        return new LotResponse(lot.getId(), lot.getProduct().getId(), lot.getLotNumber(), lot.getExpiryDate(),
                lot.getProductionDate(), lot.isExpiredOn(today));
    }

    public static StockResponse toResponse(Stock stock) {
        Lot lot = stock.getLot();
        return new StockResponse(
                stock.getId(),
                stock.getProduct().getId(),
                stock.getProduct().getSku(),
                stock.getProduct().getName(),
                stock.getWarehouse().getId(),
                stock.getWarehouse().getCode(),
                stock.getLocation().getId(),
                stock.getLocation().getCode(),
                lot == null ? null : lot.getId(),
                lot == null ? null : lot.getLotNumber(),
                lot == null ? null : lot.getExpiryDate(),
                stock.getQuantity(),
                stock.getProduct().getBaseUom().getCode());
    }

    public static MinStockResponse toResponse(StockMinRule rule) {
        return new MinStockResponse(rule.getId(), rule.getProduct().getId(), rule.getProduct().getSku(),
                rule.getWarehouse().getId(), rule.getWarehouse().getCode(), rule.getMinQuantity());
    }
}
