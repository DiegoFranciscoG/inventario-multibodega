package io.github.diegofranciscog.inventory.controller;

import java.io.IOException;
import java.time.LocalDate;

import jakarta.servlet.http.HttpServletResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.diegofranciscog.inventory.dto.kardex.KardexResponses.KardexResponse;
import io.github.diegofranciscog.inventory.service.KardexService;

@RestController
@RequestMapping("/api/kardex")
@Tag(name = "Kárdex")
public class KardexController {

    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final KardexService kardexService;

    public KardexController(KardexService kardexService) {
        this.kardexService = kardexService;
    }

    @GetMapping
    @Operation(summary = "Kárdex valorizado de un producto (opcionalmente de una bodega y un rango de fechas)")
    public KardexResponse kardex(@RequestParam Long productId,
                                 @RequestParam(required = false) Long warehouseId,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "50") int size) {
        return kardexService.kardex(productId, warehouseId, from, to, page, size);
    }

    @GetMapping(value = "/export", produces = XLSX)
    @Operation(summary = "Exporta el kárdex a Excel (.xlsx)")
    public void export(@RequestParam Long productId,
                       @RequestParam(required = false) Long warehouseId,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       HttpServletResponse response) throws IOException {
        response.setContentType(XLSX);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("kardex-producto-" + productId + ".xlsx")
                .build()
                .toString());
        kardexService.exportXlsx(productId, warehouseId, from, to, response.getOutputStream());
    }
}
