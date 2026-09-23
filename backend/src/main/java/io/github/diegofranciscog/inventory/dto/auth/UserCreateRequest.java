package io.github.diegofranciscog.inventory.dto.auth;

import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.github.diegofranciscog.inventory.entity.Role;

public record UserCreateRequest(
        @NotBlank @Email(message = "Correo inválido") @Size(max = 254) String email,
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 120) String fullName,
        @NotBlank @Size(min = 12, max = 128, message = "La contraseña debe tener entre 12 y 128 caracteres") String password,
        @NotNull(message = "El rol es obligatorio") Role role,
        Set<Long> warehouseIds) {

    @Override
    public String toString() {
        return "UserCreateRequest[email=" + email + ", role=" + role + ", password=***]";
    }
}
