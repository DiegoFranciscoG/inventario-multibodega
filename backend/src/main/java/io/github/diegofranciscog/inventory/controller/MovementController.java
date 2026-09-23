package io.github.diegofranciscog.inventory.controller;

import java.time.LocalDate;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.diegofranciscog.inventory.dto.common.PageResponse;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.AdjustmentRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.IssueRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.ReceiptRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.TransferRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementResponses.MovementResponse;
import io.github.diegofranciscog.inventory.dto.movement.MovementResponses.MovementSummaryResponse;
import io.github.diegofranciscog.inventory.entity.MovementType;
import io.github.diegofranciscog.inventory.security.CurrentUser;
import io.github.diegofranciscog.inventory.service.MovementQueryService;
import io.github.diegofranciscog.inventory.service.MovementService;

@RestController
@RequestMapping("/api/movements")
@Tag(name = "Movimientos")
public class MovementController {

    private static final String OPERATORS = "hasAnyRole('ADMIN','SUPERVISOR','OPERATOR')";

    private final MovementService movementService;
    private final MovementQueryService queryService;
    private final CurrentUser currentUser;

    public MovementController(MovementService movementService, MovementQueryService queryService, CurrentUser currentUser) {
        this.movementService = movementService;
        this.queryService = queryService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<MovementSummaryResponse> search(
            @RequestParam(required = false) MovementType type,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.search(type, warehouseId, from, to, page, size);
    }

    @GetMapping("/{id}")
    public MovementResponse get(@PathVariable Long id) {
        return queryService.get(id);
    }

    @PostMapping("/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(OPERATORS)
    @Operation(summary = "Ingreso: suma stock y recalcula el costo promedio ponderado")
    public MovementResponse receive(@Valid @RequestBody ReceiptRequest request) {
        return movementService.receive(request, currentUser.id());
    }

    @PostMapping("/issues")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(OPERATORS)
    @Operation(summary = "Egreso al costo promedio; sin ubicación/lote asigna por FEFO; nunca deja stock negativo")
    public MovementResponse issue(@Valid @RequestBody IssueRequest request) {
        return movementService.issue(request, currentUser.id());
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(OPERATORS)
    @Operation(summary = "Transferencia entre bodegas en una sola transacción")
    public MovementResponse transfer(@Valid @RequestBody TransferRequest request) {
        return movementService.transfer(request, currentUser.id());
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    @Operation(summary = "Ajuste positivo o negativo con motivo obligatorio (solo supervisores)")
    public MovementResponse adjust(@Valid @RequestBody AdjustmentRequest request) {
        return movementService.adjust(request, currentUser.id());
    }
}
