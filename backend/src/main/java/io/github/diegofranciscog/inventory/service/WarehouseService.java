package io.github.diegofranciscog.inventory.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.diegofranciscog.inventory.dto.storage.StorageRequests.LocationRequest;
import io.github.diegofranciscog.inventory.dto.storage.StorageRequests.WarehouseRequest;
import io.github.diegofranciscog.inventory.dto.storage.StorageResponses.LocationResponse;
import io.github.diegofranciscog.inventory.dto.storage.StorageResponses.WarehouseResponse;
import io.github.diegofranciscog.inventory.entity.Location;
import io.github.diegofranciscog.inventory.entity.LocationType;
import io.github.diegofranciscog.inventory.entity.Warehouse;
import io.github.diegofranciscog.inventory.exception.BusinessRuleException;
import io.github.diegofranciscog.inventory.exception.ConflictException;
import io.github.diegofranciscog.inventory.exception.NotFoundException;
import io.github.diegofranciscog.inventory.mapper.StorageMapper;
import io.github.diegofranciscog.inventory.repository.LocationRepository;
import io.github.diegofranciscog.inventory.repository.WarehouseRepository;

/** Bodegas y ubicaciones bodega → pasillo → estante → nivel (R-17). */
@Service
public class WarehouseService {

    private final WarehouseRepository warehouses;
    private final LocationRepository locations;
    private final ReferenceResolver references;
    private final AuditService audit;

    public WarehouseService(WarehouseRepository warehouses, LocationRepository locations, ReferenceResolver references,
                            AuditService audit) {
        this.warehouses = warehouses;
        this.locations = locations;
        this.references = references;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> warehouses() {
        return warehouses.findAllByOrderByCodeAsc().stream().map(StorageMapper::toResponse).toList();
    }

    @Transactional
    public WarehouseResponse create(WarehouseRequest request) {
        if (warehouses.existsByCode(request.code())) {
            throw new ConflictException("DUPLICATE_WAREHOUSE", "Ya existe la bodega " + request.code());
        }
        Warehouse warehouse = warehouses.save(new Warehouse(request.code(), request.name().strip(), request.city(),
                request.address()));
        audit.record("WAREHOUSE_CREATED", "Warehouse", warehouse.getId(), Map.of("code", warehouse.getCode()));
        return StorageMapper.toResponse(warehouse);
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseRequest request) {
        Warehouse warehouse = references.warehouse(id);
        if (!warehouse.getCode().equals(request.code())) {
            throw new BusinessRuleException("IMMUTABLE_CODE", "El código de la bodega no se puede cambiar");
        }
        warehouse.update(request.name().strip(), request.city(), request.address(),
                request.active() == null || request.active());
        audit.record("WAREHOUSE_UPDATED", "Warehouse", warehouse.getId(), Map.of("code", warehouse.getCode()));
        return StorageMapper.toResponse(warehouse);
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> locations(Long warehouseId) {
        references.warehouse(warehouseId);
        return locations.findByWarehouse(warehouseId).stream().map(StorageMapper::toResponse).toList();
    }

    @Transactional
    public LocationResponse createLocation(Long warehouseId, LocationRequest request) {
        Warehouse warehouse = references.warehouse(warehouseId);
        String code = Location.buildCode(request.aisle(), request.rack(), request.level());
        if (locations.existsByWarehouseIdAndCode(warehouseId, code)) {
            throw new ConflictException("DUPLICATE_LOCATION", "Ya existe la ubicación %s en %s".formatted(code, warehouse.getCode()));
        }
        LocationType type = request.type() == null ? LocationType.STORAGE : request.type();
        Location location = locations.save(new Location(warehouse, request.aisle(), request.rack(), request.level(), type));
        audit.record("LOCATION_CREATED", "Location", location.getId(),
                Map.of("warehouse", warehouse.getCode(), "code", location.getCode()));
        return StorageMapper.toResponse(location);
    }

    @Transactional
    public LocationResponse updateLocation(Long warehouseId, Long locationId, LocationRequest request) {
        Location location = locations.findById(locationId)
                .filter(candidate -> candidate.getWarehouse().getId().equals(warehouseId))
                .orElseThrow(() -> new NotFoundException("Ubicación", locationId));
        location.update(request.type() == null ? location.getType() : request.type(),
                request.active() == null || request.active());
        audit.record("LOCATION_UPDATED", "Location", location.getId(), Map.of("code", location.getCode()));
        return StorageMapper.toResponse(location);
    }
}
