package io.github.diegofranciscog.inventory.controller;

import java.util.List;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.diegofranciscog.inventory.dto.storage.StorageRequests.LocationRequest;
import io.github.diegofranciscog.inventory.dto.storage.StorageRequests.WarehouseRequest;
import io.github.diegofranciscog.inventory.dto.storage.StorageResponses.LocationResponse;
import io.github.diegofranciscog.inventory.dto.storage.StorageResponses.WarehouseResponse;
import io.github.diegofranciscog.inventory.service.WarehouseService;

@RestController
@RequestMapping("/api/warehouses")
@Tag(name = "Bodegas y ubicaciones")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping
    public List<WarehouseResponse> list() {
        return warehouseService.warehouses();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public WarehouseResponse create(@Valid @RequestBody WarehouseRequest request) {
        return warehouseService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public WarehouseResponse update(@PathVariable Long id, @Valid @RequestBody WarehouseRequest request) {
        return warehouseService.update(id, request);
    }

    @GetMapping("/{id}/locations")
    public List<LocationResponse> locations(@PathVariable Long id) {
        return warehouseService.locations(id);
    }

    @PostMapping("/{id}/locations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public LocationResponse createLocation(@PathVariable Long id, @Valid @RequestBody LocationRequest request) {
        return warehouseService.createLocation(id, request);
    }

    @PutMapping("/{id}/locations/{locationId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public LocationResponse updateLocation(@PathVariable Long id, @PathVariable Long locationId,
                                           @Valid @RequestBody LocationRequest request) {
        return warehouseService.updateLocation(id, locationId, request);
    }
}
