package io.github.diegofranciscog.inventory.dto.movement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Solicitudes de movimientos. Todas exigen documento de referencia (R-22) y limitan el número de renglones
 * (OWASP API4: consumo de recursos).
 */
public final class MovementRequests {

    private static final String LOT_PATTERN = "^[A-Za-z0-9._/-]{1,20}$";
    private static final String LOT_MESSAGE = "Lote: 1 a 20 caracteres (letras, números, . _ / -)";

    private MovementRequests() {
    }

    public record ReceiptRequest(
            @NotNull(message = "La bodega es obligatoria") Long warehouseId,
            @NotBlank(message = "El tipo de documento es obligatorio") String referenceTypeCode,
            @NotBlank(message = "El número de documento es obligatorio") @Size(max = 40) String referenceNumber,
            @Size(max = 255) String reason,
            @NotEmpty(message = "Debe incluir al menos un renglón") @Size(max = 100) @Valid List<ReceiptLine> lines) {
    }

    public record ReceiptLine(
            @NotNull Long productId,
            @NotNull(message = "La ubicación es obligatoria") Long locationId,
            @NotNull @Positive(message = "La cantidad debe ser mayor que cero") @Digits(integer = 14, fraction = 4)
            BigDecimal quantity,
            String uomCode,
            @NotNull(message = "El costo unitario es obligatorio") @PositiveOrZero @Digits(integer = 14, fraction = 6)
            BigDecimal unitCost,
            @Pattern(regexp = LOT_PATTERN, message = LOT_MESSAGE) String lotNumber,
            LocalDate expiryDate,
            LocalDate productionDate) {
    }

    public record IssueRequest(
            @NotNull(message = "La bodega es obligatoria") Long warehouseId,
            @NotBlank(message = "El tipo de documento es obligatorio") String referenceTypeCode,
            @NotBlank(message = "El número de documento es obligatorio") @Size(max = 40) String referenceNumber,
            @Size(max = 255) String reason,
            @NotEmpty(message = "Debe incluir al menos un renglón") @Size(max = 100) @Valid List<IssueLine> lines) {
    }

    /** Si no se indica ubicación ni lote, el sistema asigna por FEFO (R-18). */
    public record IssueLine(
            @NotNull Long productId,
            @NotNull @Positive(message = "La cantidad debe ser mayor que cero") @Digits(integer = 14, fraction = 4)
            BigDecimal quantity,
            String uomCode,
            Long locationId,
            Long lotId) {
    }

    public record TransferRequest(
            @NotNull(message = "La bodega de origen es obligatoria") Long sourceWarehouseId,
            @NotNull(message = "La bodega de destino es obligatoria") Long targetWarehouseId,
            @NotBlank(message = "El tipo de documento es obligatorio") String referenceTypeCode,
            @NotBlank(message = "El número de documento es obligatorio") @Size(max = 40) String referenceNumber,
            @Size(max = 255) String reason,
            @NotEmpty(message = "Debe incluir al menos un renglón") @Size(max = 100) @Valid List<TransferLine> lines) {
    }

    public record TransferLine(
            @NotNull Long productId,
            @NotNull @Positive(message = "La cantidad debe ser mayor que cero") @Digits(integer = 14, fraction = 4)
            BigDecimal quantity,
            String uomCode,
            Long fromLocationId,
            Long lotId,
            @NotNull(message = "La ubicación de destino es obligatoria") Long toLocationId) {
    }

    public record AdjustmentRequest(
            @NotNull(message = "La bodega es obligatoria") Long warehouseId,
            @NotBlank(message = "El tipo de documento es obligatorio") String referenceTypeCode,
            @NotBlank(message = "El número de documento es obligatorio") @Size(max = 40) String referenceNumber,
            @NotBlank(message = "El motivo del ajuste es obligatorio") @Size(max = 255) String reason,
            @NotEmpty(message = "Debe incluir al menos un renglón") @Size(max = 100) @Valid List<AdjustmentLine> lines) {
    }

    /** {@code quantityDelta} positivo suma stock; negativo lo resta. */
    public record AdjustmentLine(
            @NotNull Long productId,
            @NotNull(message = "La ubicación es obligatoria") Long locationId,
            Long lotId,
            @Pattern(regexp = LOT_PATTERN, message = LOT_MESSAGE) String lotNumber,
            LocalDate expiryDate,
            @NotNull(message = "La cantidad es obligatoria") @Digits(integer = 14, fraction = 4) BigDecimal quantityDelta,
            @PositiveOrZero @Digits(integer = 14, fraction = 6) BigDecimal unitCost) {
    }
}
