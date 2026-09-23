package io.github.diegofranciscog.inventory.mapper;

import java.math.BigDecimal;
import java.util.List;

import io.github.diegofranciscog.inventory.dto.kardex.KardexResponses.KardexEntry;
import io.github.diegofranciscog.inventory.dto.movement.MovementResponses.MovementLineResponse;
import io.github.diegofranciscog.inventory.dto.movement.MovementResponses.MovementResponse;
import io.github.diegofranciscog.inventory.dto.movement.MovementResponses.MovementSummaryResponse;
import io.github.diegofranciscog.inventory.entity.Direction;
import io.github.diegofranciscog.inventory.entity.InventoryMovement;
import io.github.diegofranciscog.inventory.entity.MovementLine;

public final class MovementMapper {

    private MovementMapper() {
    }

    /**
     * Valor del movimiento: ingresos y egresos suman sus renglones; la transferencia vale lo que salió del origen; el
     * ajuste es neto (entradas − salidas).
     */
    public static BigDecimal totalCost(InventoryMovement movement, List<MovementLine> lines) {
        BigDecimal in = sum(lines, Direction.IN);
        BigDecimal out = sum(lines, Direction.OUT);
        return switch (movement.getType()) {
            case RECEIPT -> in;
            case ISSUE, TRANSFER -> out;
            case ADJUSTMENT -> in.subtract(out);
        };
    }

    private static BigDecimal sum(List<MovementLine> lines, Direction direction) {
        return lines.stream()
                .filter(line -> line.getDirection() == direction)
                .map(MovementLine::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static MovementResponse toResponse(InventoryMovement movement, List<MovementLine> lines) {
        BigDecimal totalCost = totalCost(movement, lines);
        return new MovementResponse(
                movement.getId(),
                movement.getNumber(),
                movement.getType(),
                UserMapper.toRef(movement.getWarehouse()),
                UserMapper.toRef(movement.getTargetWarehouse()),
                movement.getReferenceType().getCode(),
                movement.getReferenceType().getName(),
                movement.getReferenceNumber(),
                movement.getReason(),
                movement.getOccurredAt(),
                movement.getCreatedBy().getEmail(),
                movement.getCycleCountId(),
                totalCost,
                lines.stream().map(MovementMapper::toLineResponse).toList());
    }

    public static MovementLineResponse toLineResponse(MovementLine line) {
        return new MovementLineResponse(
                line.getLineNo(),
                line.getProduct().getId(),
                line.getProduct().getSku(),
                line.getProduct().getName(),
                line.getWarehouse().getCode(),
                line.getLocation().getCode(),
                line.getLot() == null ? null : line.getLot().getLotNumber(),
                line.getDirection(),
                line.getQuantity(),
                line.getUomCode(),
                line.getUomQuantity(),
                line.getUnitCost(),
                line.getTotalCost());
    }

    public static MovementSummaryResponse toSummary(InventoryMovement movement) {
        return new MovementSummaryResponse(
                movement.getId(),
                movement.getNumber(),
                movement.getType(),
                movement.getWarehouse().getCode(),
                movement.getTargetWarehouse() == null ? null : movement.getTargetWarehouse().getCode(),
                movement.getReferenceType().getCode(),
                movement.getReferenceNumber(),
                movement.getOccurredAt(),
                movement.getCreatedBy().getEmail());
    }

    public static KardexEntry toKardexEntry(MovementLine line) {
        InventoryMovement movement = line.getMovement();
        return new KardexEntry(
                line.getId(),
                line.getOccurredAt(),
                movement.getNumber(),
                movement.getType(),
                movement.getReferenceType().getCode(),
                movement.getReferenceNumber(),
                line.getWarehouse().getCode(),
                line.getLocation().getCode(),
                line.getLot() == null ? null : line.getLot().getLotNumber(),
                line.getDirection(),
                line.getQuantity(),
                line.getUnitCost(),
                line.getTotalCost(),
                line.getBalanceQuantity(),
                line.getBalanceValue(),
                line.getAverageCost(),
                line.getWarehouseBalanceQuantity());
    }
}
