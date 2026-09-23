package io.github.diegofranciscog.inventory.mapper;

import java.util.Comparator;

import io.github.diegofranciscog.inventory.dto.auth.UserResponse;
import io.github.diegofranciscog.inventory.dto.common.WarehouseRef;
import io.github.diegofranciscog.inventory.entity.AppUser;
import io.github.diegofranciscog.inventory.entity.Warehouse;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.isActive(),
                user.getCreatedAt(),
                user.getWarehouses().stream()
                        .sorted(Comparator.comparing(Warehouse::getCode))
                        .map(UserMapper::toRef)
                        .toList());
    }

    public static WarehouseRef toRef(Warehouse warehouse) {
        return warehouse == null ? null : new WarehouseRef(warehouse.getId(), warehouse.getCode(), warehouse.getName());
    }
}
