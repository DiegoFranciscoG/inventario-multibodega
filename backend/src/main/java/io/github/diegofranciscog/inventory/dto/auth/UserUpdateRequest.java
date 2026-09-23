package io.github.diegofranciscog.inventory.dto.auth;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.github.diegofranciscog.inventory.entity.Role;

public record UserUpdateRequest(
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 120) String fullName,
        @NotNull(message = "El rol es obligatorio") Role role,
        boolean active,
        Set<Long> warehouseIds,
        @Size(min = 12, max = 128, message = "La contraseña debe tener entre 12 y 128 caracteres") String newPassword) {

    @Override
    public String toString() {
        return "UserUpdateRequest[fullName=" + fullName + ", role=" + role + ", active=" + active + "]";
    }
}
