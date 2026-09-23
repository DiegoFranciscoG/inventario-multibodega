package io.github.diegofranciscog.inventory.dto.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Texto leído por la cámara (puede contener el separador GS de GS1, por eso viaja en el cuerpo y no en la URL). */
public record ScanRequest(@NotBlank(message = "El código es obligatorio") @Size(max = 512) String code) {
}
