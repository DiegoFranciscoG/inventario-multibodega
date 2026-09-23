package io.github.diegofranciscog.inventory.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio") @Email(message = "Correo inválido") @Size(max = 254) String email,
        @NotBlank(message = "La contraseña es obligatoria") @Size(max = 128) String password) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
