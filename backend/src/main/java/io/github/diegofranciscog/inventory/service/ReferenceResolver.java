package io.github.diegofranciscog.inventory.service;

import java.util.Objects;

import org.springframework.stereotype.Component;

import io.github.diegofranciscog.inventory.entity.AppUser;
import io.github.diegofranciscog.inventory.entity.DocumentType;
import io.github.diegofranciscog.inventory.entity.Location;
import io.github.diegofranciscog.inventory.entity.Lot;
import io.github.diegofranciscog.inventory.entity.Product;
import io.github.diegofranciscog.inventory.entity.Warehouse;
import io.github.diegofranciscog.inventory.exception.BusinessRuleException;
import io.github.diegofranciscog.inventory.exception.ForbiddenOperationException;
import io.github.diegofranciscog.inventory.exception.NotFoundException;
import io.github.diegofranciscog.inventory.repository.AppUserRepository;
import io.github.diegofranciscog.inventory.repository.DocumentTypeRepository;
import io.github.diegofranciscog.inventory.repository.LocationRepository;
import io.github.diegofranciscog.inventory.repository.LotRepository;
import io.github.diegofranciscog.inventory.repository.ProductRepository;
import io.github.diegofranciscog.inventory.repository.WarehouseRepository;

/** Carga y valida las referencias que usan las operaciones de inventario (existencia, estado activo, pertenencia). */
@Component
public class ReferenceResolver {

    private final AppUserRepository users;
    private final WarehouseRepository warehouses;
    private final LocationRepository locations;
    private final ProductRepository products;
    private final LotRepository lots;
    private final DocumentTypeRepository documentTypes;

    public ReferenceResolver(AppUserRepository users, WarehouseRepository warehouses, LocationRepository locations,
                             ProductRepository products, LotRepository lots, DocumentTypeRepository documentTypes) {
        this.users = users;
        this.warehouses = warehouses;
        this.locations = locations;
        this.products = products;
        this.lots = lots;
        this.documentTypes = documentTypes;
    }

    public AppUser activeUser(Long id) {
        AppUser user = users.findWithWarehousesById(id).orElseThrow(() -> new NotFoundException("Usuario", id));
        if (!user.isActive()) {
            throw new ForbiddenOperationException("USER_INACTIVE", "El usuario está desactivado");
        }
        return user;
    }

    public Warehouse warehouse(Long id) {
        return warehouses.findById(id).orElseThrow(() -> new NotFoundException("Bodega", id));
    }

    public Warehouse activeWarehouse(Long id) {
        Warehouse warehouse = warehouse(id);
        if (!warehouse.isActive()) {
            throw new BusinessRuleException("WAREHOUSE_INACTIVE", "La bodega %s está inactiva".formatted(warehouse.getCode()));
        }
        return warehouse;
    }

    /** La ubicación debe existir, estar activa y pertenecer a la bodega indicada. */
    public Location activeLocation(Long id, Warehouse warehouse) {
        Location location = locations.findById(id).orElseThrow(() -> new NotFoundException("Ubicación", id));
        if (!Objects.equals(location.getWarehouse().getId(), warehouse.getId())) {
            throw new BusinessRuleException("LOCATION_NOT_IN_WAREHOUSE",
                    "La ubicación %s no pertenece a la bodega %s".formatted(location.getCode(), warehouse.getCode()));
        }
        if (!location.isActive()) {
            throw new BusinessRuleException("LOCATION_INACTIVE", "La ubicación %s está inactiva".formatted(location.getCode()));
        }
        return location;
    }

    public Product product(Long id) {
        return products.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Producto", id));
    }

    public Product activeProduct(Long id) {
        Product product = product(id);
        if (!product.isActive()) {
            throw new BusinessRuleException("PRODUCT_INACTIVE", "El producto %s está inactivo".formatted(product.getSku()));
        }
        return product;
    }

    /** El lote debe pertenecer al producto. */
    public Lot lotOf(Product product, Long lotId) {
        Lot lot = lots.findById(lotId).orElseThrow(() -> new NotFoundException("Lote", lotId));
        if (!Objects.equals(lot.getProduct().getId(), product.getId())) {
            throw new BusinessRuleException("LOT_PRODUCT_MISMATCH",
                    "El lote %s no pertenece al producto %s".formatted(lot.getLotNumber(), product.getSku()));
        }
        return lot;
    }

    public DocumentType documentType(String code) {
        return documentTypes.findById(code)
                .orElseThrow(() -> new BusinessRuleException("UNKNOWN_DOCUMENT_TYPE", "Tipo de documento desconocido: " + code));
    }
}
