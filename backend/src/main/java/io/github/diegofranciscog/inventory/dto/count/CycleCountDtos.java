package io.github.diegofranciscog.inventory.dto.count;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import io.github.diegofranciscog.inventory.entity.AbcClass;
import io.github.diegofranciscog.inventory.entity.CycleCountStatus;

/** Solicitudes y respuestas del conteo cíclico (R-20, R-21). */
public final class CycleCountDtos {

    private CycleCountDtos() {
    }

    public record CreateRequest(
            @NotNull(message = "La bodega es obligatoria") Long warehouseId,
            @Pattern(regexp = "^[A-Z0-9]{1,3}$", message = "Pasillo: 1 a 3 caracteres A-Z o 0-9") String aisle,
            AbcClass abcClass,
            @Size(max = 255) String notes) {
    }

    public record CountLineRequest(
            @NotNull(message = "La cantidad contada es obligatoria") @PositiveOrZero(message = "No puede ser negativa")
            @Digits(integer = 14, fraction = 4) BigDecimal countedQuantity,
            @Size(max = 255) String note) {
    }

    /** Producto encontrado en una ubicación donde el sistema no lo esperaba. */
    public record AddLineRequest(
            @NotNull Long locationId,
            @NotNull Long productId,
            @Pattern(regexp = "^[A-Za-z0-9._/-]{1,20}$", message = "Lote: 1 a 20 caracteres") String lotNumber,
            LocalDate expiryDate,
            @NotNull @PositiveOrZero @Digits(integer = 14, fraction = 4) BigDecimal countedQuantity,
            @Size(max = 255) String note) {
    }

    public record ReviewRequest(@Size(max = 500) String notes) {
    }

    public record CycleCountResponse(
            Long id,
            String number,
            Long warehouseId,
            String warehouseCode,
            CycleCountStatus status,
            String aisleFilter,
            AbcClass abcFilter,
            String notes,
            String createdBy,
            Instant createdAt,
            String submittedBy,
            Instant submittedAt,
            String reviewedBy,
            Instant reviewedAt,
            String reviewNotes,
            String adjustmentMovementNumber,
            int totalLines,
            long countedLines,
            long linesWithDifference,
            BigDecimal differenceValue,
            boolean blind,
            List<LineResponse> lines) {
    }

    /** En un conteo abierto (ciego) {@code systemQuantity} y {@code difference} van en {@code null}. */
    public record LineResponse(
            Long id,
            Long locationId,
            String locationCode,
            Long productId,
            String sku,
            String productName,
            String gtin,
            Long lotId,
            String lotNumber,
            BigDecimal countedQuantity,
            BigDecimal systemQuantity,
            BigDecimal difference,
            BigDecimal differenceValue,
            String countedBy,
            Instant countedAt,
            String note) {
    }

    public record SummaryResponse(
            Long id,
            String number,
            String warehouseCode,
            CycleCountStatus status,
            String createdBy,
            Instant createdAt,
            String submittedBy,
            String reviewedBy) {
    }
}
