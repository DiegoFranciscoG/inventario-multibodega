package io.github.diegofranciscog.inventory.service;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.diegofranciscog.inventory.config.AppProperties;
import io.github.diegofranciscog.inventory.dto.common.PageResponse;
import io.github.diegofranciscog.inventory.dto.movement.MovementResponses.MovementResponse;
import io.github.diegofranciscog.inventory.dto.movement.MovementResponses.MovementSummaryResponse;
import io.github.diegofranciscog.inventory.entity.InventoryMovement;
import io.github.diegofranciscog.inventory.entity.MovementType;
import io.github.diegofranciscog.inventory.exception.NotFoundException;
import io.github.diegofranciscog.inventory.mapper.MovementMapper;
import io.github.diegofranciscog.inventory.repository.InventoryMovementRepository;
import io.github.diegofranciscog.inventory.repository.MovementLineRepository;

@Service
@Transactional(readOnly = true)
public class MovementQueryService {

    private final InventoryMovementRepository movements;
    private final MovementLineRepository lines;
    private final AppProperties properties;

    public MovementQueryService(InventoryMovementRepository movements, MovementLineRepository lines,
                                AppProperties properties) {
        this.movements = movements;
        this.lines = lines;
        this.properties = properties;
    }

    public MovementResponse get(Long id) {
        InventoryMovement movement = movements.findWithLinesById(id).orElseThrow(() -> new NotFoundException("Movimiento", id));
        return MovementMapper.toResponse(movement, lines.findByMovement(id));
    }

    public PageResponse<MovementSummaryResponse> search(MovementType type, Long warehouseId, LocalDate from, LocalDate to,
                                                        int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Pagination.clamp(size, properties),
                Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
        Instant start = from == null ? DateRange.MIN : startOf(from);
        Instant end = to == null ? DateRange.MAX : startOf(to.plusDays(1));
        return PageResponse.from(movements.search(type, warehouseId, start, end, pageable), MovementMapper::toSummary);
    }

    private Instant startOf(LocalDate date) {
        return date.atStartOfDay(properties.inventory().timeZone()).toInstant();
    }
}
