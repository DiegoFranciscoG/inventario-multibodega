package io.github.diegofranciscog.inventory.controller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.diegofranciscog.inventory.dto.common.PageResponse;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.AddLineRequest;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.CountLineRequest;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.CreateRequest;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.CycleCountResponse;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.ReviewRequest;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.SummaryResponse;
import io.github.diegofranciscog.inventory.entity.CycleCountStatus;
import io.github.diegofranciscog.inventory.security.CurrentUser;
import io.github.diegofranciscog.inventory.service.CycleCountService;

@RestController
@RequestMapping("/api/cycle-counts")
@Tag(name = "Conteo cíclico")
public class CycleCountController {

    private static final String COUNTERS = "hasAnyRole('ADMIN','SUPERVISOR','OPERATOR')";
    private static final String REVIEWERS = "hasAnyRole('ADMIN','SUPERVISOR')";

    private final CycleCountService cycleCountService;
    private final CurrentUser currentUser;

    public CycleCountController(CycleCountService cycleCountService, CurrentUser currentUser) {
        this.cycleCountService = cycleCountService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<SummaryResponse> search(@RequestParam(required = false) Long warehouseId,
                                                @RequestParam(required = false) CycleCountStatus status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return cycleCountService.search(warehouseId, status, page, size);
    }

    @GetMapping("/{id}")
    public CycleCountResponse get(@PathVariable Long id) {
        return cycleCountService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(COUNTERS)
    @Operation(summary = "Crea un conteo ciego para una bodega (filtros opcionales: pasillo y clase ABC)")
    public CycleCountResponse create(@Valid @RequestBody CreateRequest request) {
        return cycleCountService.create(request, currentUser.id());
    }

    @PutMapping("/{id}/lines/{lineId}")
    @PreAuthorize(COUNTERS)
    public CycleCountResponse count(@PathVariable Long id, @PathVariable Long lineId,
                                    @Valid @RequestBody CountLineRequest request) {
        return cycleCountService.registerCount(id, lineId, request, currentUser.id());
    }

    @PostMapping("/{id}/lines")
    @PreAuthorize(COUNTERS)
    @Operation(summary = "Agrega un producto encontrado donde el sistema no lo esperaba")
    public CycleCountResponse addLine(@PathVariable Long id, @Valid @RequestBody AddLineRequest request) {
        return cycleCountService.addLine(id, request, currentUser.id());
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(COUNTERS)
    public CycleCountResponse submit(@PathVariable Long id) {
        return cycleCountService.submit(id, currentUser.id());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(REVIEWERS)
    @Operation(summary = "Aprueba el conteo y registra el ajuste (quien contó no puede aprobar)")
    public CycleCountResponse approve(@PathVariable Long id, @Valid @RequestBody(required = false) ReviewRequest request) {
        return cycleCountService.approve(id, request, currentUser.id());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(REVIEWERS)
    public CycleCountResponse reject(@PathVariable Long id, @Valid @RequestBody(required = false) ReviewRequest request) {
        return cycleCountService.reject(id, request, currentUser.id());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(REVIEWERS)
    public CycleCountResponse cancel(@PathVariable Long id) {
        return cycleCountService.cancel(id, currentUser.id());
    }
}
