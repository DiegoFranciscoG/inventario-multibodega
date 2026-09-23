package io.github.diegofranciscog.inventory.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9_-]{2,20}$", message = "Código: 2 a 20 caracteres A-Z, 0-9, _ o -") String code,
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 80) String name,
        Boolean active) {
}
