package io.github.diegofranciscog.inventory.dto.auth;

import java.time.Instant;
import java.util.List;

import io.github.diegofranciscog.inventory.dto.common.WarehouseRef;
import io.github.diegofranciscog.inventory.entity.Role;

public record UserResponse(Long id, String email, String fullName, Role role, boolean active, Instant createdAt,
                           List<WarehouseRef> warehouses) {
}
