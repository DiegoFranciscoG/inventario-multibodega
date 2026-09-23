package io.github.diegofranciscog.inventory.service;

import org.springframework.stereotype.Service;

import io.github.diegofranciscog.inventory.entity.AppUser;
import io.github.diegofranciscog.inventory.entity.Warehouse;
import io.github.diegofranciscog.inventory.exception.ForbiddenOperationException;

/**
 * Autorización a nivel de objeto (OWASP API1 — BOLA): un operador solo registra movimientos y conteos en las bodegas
 * que tiene asignadas; los demás roles operan en todas (R-25).
 */
@Service
public class WarehouseAccessService {

    public void requireCanOperate(AppUser user, Warehouse warehouse) {
        if (!user.canOperateIn(warehouse.getId())) {
            throw new ForbiddenOperationException("WAREHOUSE_FORBIDDEN",
                    "No tienes permiso para operar en la bodega %s".formatted(warehouse.getCode()));
        }
    }
}
