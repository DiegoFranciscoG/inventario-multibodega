package io.github.diegofranciscog.inventory.mapper;

import java.math.BigDecimal;

import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.CycleCountResponse;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.LineResponse;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.SummaryResponse;
import io.github.diegofranciscog.inventory.entity.AppUser;
import io.github.diegofranciscog.inventory.entity.CycleCount;
import io.github.diegofranciscog.inventory.entity.CycleCountLine;
import io.github.diegofranciscog.inventory.entity.CycleCountStatus;

public final class CycleCountMapper {

    private CycleCountMapper() {
    }

    /** Mientras el conteo está abierto es ciego: no se exponen cantidades del sistema ni diferencias (R-20). */
    public static CycleCountResponse toResponse(CycleCount count) {
        boolean blind = count.getStatus() == CycleCountStatus.OPEN;
        long counted = count.getLines().stream().filter(CycleCountLine::isCounted).count();
        long withDifference = blind ? 0 : count.getLines().stream().filter(CycleCountLine::hasDifference).count();
        BigDecimal differenceValue = blind ? null : count.getLines().stream()
                .map(CycleCountLine::differenceValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CycleCountResponse(
                count.getId(),
                count.getNumber(),
                count.getWarehouse().getId(),
                count.getWarehouse().getCode(),
                count.getStatus(),
                count.getAisleFilter(),
                count.getAbcFilter(),
                count.getNotes(),
                email(count.getCreatedBy()),
                count.getCreatedAt(),
                email(count.getSubmittedBy()),
                count.getSubmittedAt(),
                email(count.getReviewedBy()),
                count.getReviewedAt(),
                count.getReviewNotes(),
                count.getAdjustmentMovement() == null ? null : count.getAdjustmentMovement().getNumber(),
                count.getLines().size(),
                counted,
                withDifference,
                differenceValue,
                blind,
                count.getLines().stream().map(line -> toLineResponse(line, blind)).toList());
    }

    public static LineResponse toLineResponse(CycleCountLine line, boolean blind) {
        return new LineResponse(
                line.getId(),
                line.getLocation().getId(),
                line.getLocation().getCode(),
                line.getProduct().getId(),
                line.getProduct().getSku(),
                line.getProduct().getName(),
                line.getProduct().getGtin(),
                line.getLot() == null ? null : line.getLot().getId(),
                line.getLot() == null ? null : line.getLot().getLotNumber(),
                line.getCountedQuantity(),
                blind ? null : line.getSystemQuantity(),
                blind ? null : line.getDifference(),
                blind ? null : line.differenceValue(),
                email(line.getCountedBy()),
                line.getCountedAt(),
                line.getNote());
    }

    public static SummaryResponse toSummary(CycleCount count) {
        return new SummaryResponse(count.getId(), count.getNumber(), count.getWarehouse().getCode(), count.getStatus(),
                email(count.getCreatedBy()), count.getCreatedAt(), email(count.getSubmittedBy()),
                email(count.getReviewedBy()));
    }

    private static String email(AppUser user) {
        return user == null ? null : user.getEmail();
    }
}
