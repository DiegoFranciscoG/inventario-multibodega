package io.github.diegofranciscog.inventory.controller;

import java.util.List;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.diegofranciscog.inventory.dto.common.PageResponse;
import io.github.diegofranciscog.inventory.dto.report.ReportResponses.AuditLogResponse;
import io.github.diegofranciscog.inventory.dto.report.ReportResponses.DashboardResponse;
import io.github.diegofranciscog.inventory.dto.report.ReportResponses.ExpiringLotAlert;
import io.github.diegofranciscog.inventory.dto.report.ReportResponses.LowStockAlert;
import io.github.diegofranciscog.inventory.dto.report.ReportResponses.ReconciliationResponse;
import io.github.diegofranciscog.inventory.dto.report.ReportResponses.ScanResponse;
import io.github.diegofranciscog.inventory.dto.report.ScanRequest;
import io.github.diegofranciscog.inventory.dto.storage.StorageResponses.StockResponse;
import io.github.diegofranciscog.inventory.mapper.AuditMapper;
import io.github.diegofranciscog.inventory.service.AlertService;
import io.github.diegofranciscog.inventory.service.AuditService;
import io.github.diegofranciscog.inventory.service.ReportService;
import io.github.diegofranciscog.inventory.service.ScanService;
import io.github.diegofranciscog.inventory.service.StockService;

/** Consultas de solo lectura: stock, alertas, escaneo, reportes y bitácora de auditoría. */
@RestController
@RequestMapping("/api")
@Tag(name = "Consultas y reportes")
public class InventoryQueryController {

    private static final int MAX_AUDIT_PAGE = 100;

    private final StockService stockService;
    private final AlertService alertService;
    private final ScanService scanService;
    private final ReportService reportService;
    private final AuditService auditService;

    public InventoryQueryController(StockService stockService, AlertService alertService, ScanService scanService,
                                    ReportService reportService, AuditService auditService) {
        this.stockService = stockService;
        this.alertService = alertService;
        this.scanService = scanService;
        this.reportService = reportService;
        this.auditService = auditService;
    }

    @GetMapping("/stock")
    public PageResponse<StockResponse> stock(@RequestParam(required = false) Long warehouseId,
                                             @RequestParam(required = false) Long productId,
                                             @RequestParam(required = false) Long locationId,
                                             @RequestParam(defaultValue = "false") boolean includeZero,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return stockService.search(warehouseId, productId, locationId, includeZero, page, size);
    }

    @GetMapping("/alerts/low-stock")
    public List<LowStockAlert> lowStock() {
        return alertService.lowStock();
    }

    @GetMapping("/alerts/expiring-lots")
    public List<ExpiringLotAlert> expiringLots(@RequestParam(required = false) Integer days) {
        return alertService.expiringLots(days);
    }

    @PostMapping("/scan")
    @Operation(summary = "Interpreta un código leído: GTIN, GS1-128/DataMatrix/QR, GS1 Digital Link, QR de ubicación o SKU")
    public ScanResponse scan(@Valid @RequestBody ScanRequest request) {
        return scanService.resolve(request.code());
    }

    @GetMapping("/reports/reconciliation")
    @Operation(summary = "Conciliación: el kárdex debe cuadrar con el stock y con la valorización")
    public ReconciliationResponse reconciliation() {
        return reportService.reconciliation();
    }

    @GetMapping("/reports/dashboard")
    public DashboardResponse dashboard() {
        return reportService.dashboard();
    }

    @GetMapping("/audit-log")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public PageResponse<AuditLogResponse> auditLog(@RequestParam(required = false) String entityType,
                                                   @RequestParam(required = false) String action,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "50") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_AUDIT_PAGE),
                Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
        return PageResponse.from(auditService.search(entityType, action, pageable), AuditMapper::toResponse);
    }
}
