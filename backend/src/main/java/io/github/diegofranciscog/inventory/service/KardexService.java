package io.github.diegofranciscog.inventory.service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.diegofranciscog.inventory.config.AppProperties;
import io.github.diegofranciscog.inventory.dto.common.PageResponse;
import io.github.diegofranciscog.inventory.dto.kardex.KardexResponses.KardexEntry;
import io.github.diegofranciscog.inventory.dto.kardex.KardexResponses.KardexResponse;
import io.github.diegofranciscog.inventory.dto.kardex.KardexResponses.KardexTotals;
import io.github.diegofranciscog.inventory.entity.MovementLine;
import io.github.diegofranciscog.inventory.entity.Product;
import io.github.diegofranciscog.inventory.entity.Warehouse;
import io.github.diegofranciscog.inventory.exception.BusinessRuleException;
import io.github.diegofranciscog.inventory.mapper.MovementMapper;
import io.github.diegofranciscog.inventory.mapper.UserMapper;
import io.github.diegofranciscog.inventory.repository.MovementLineRepository;

/** Kárdex valorizado por producto (y opcionalmente por bodega) — R-06, R-09. */
@Service
@Transactional(readOnly = true)
public class KardexService {

    public static final String COST_METHOD = "Costo promedio ponderado móvil (NIC 2, párr. 25 y 27)";

    private final MovementLineRepository lines;
    private final ReferenceResolver references;
    private final KardexExcelExporter excelExporter;
    private final AppProperties properties;

    public KardexService(MovementLineRepository lines, ReferenceResolver references, KardexExcelExporter excelExporter,
                         AppProperties properties) {
        this.lines = lines;
        this.references = references;
        this.excelExporter = excelExporter;
        this.properties = properties;
    }

    public KardexResponse kardex(Long productId, Long warehouseId, LocalDate from, LocalDate to, int page, int size) {
        Product product = references.product(productId);
        Warehouse warehouse = warehouseId == null ? null : references.warehouse(warehouseId);
        Range range = range(from, to);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Pagination.clamp(size, properties));
        PageResponse<KardexEntry> entries = PageResponse.from(
                lines.findKardex(productId, warehouseId, range.from(), range.to(), pageable), MovementMapper::toKardexEntry);
        MovementLineRepository.Totals totals = lines.totals(productId, warehouseId, range.from(), range.to());
        return new KardexResponse(product.getId(), product.getSku(), product.getName(), product.getBaseUom().getCode(),
                UserMapper.toRef(warehouse), COST_METHOD,
                new KardexTotals(totals.inQuantity(), totals.inValue(), totals.outQuantity(), totals.outValue()),
                entries);
    }

    /** Exporta el kárdex a .xlsx con un límite de filas (OWASP API4). */
    public void exportXlsx(Long productId, Long warehouseId, LocalDate from, LocalDate to, OutputStream output)
            throws IOException {
        Product product = references.product(productId);
        Warehouse warehouse = warehouseId == null ? null : references.warehouse(warehouseId);
        Range range = range(from, to);
        long rows = lines.countKardex(productId, warehouseId, range.from(), range.to());
        int maxRows = properties.inventory().exportMaxRows();
        if (rows > maxRows) {
            throw new BusinessRuleException("EXPORT_TOO_LARGE",
                    "El kárdex tiene %d renglones; el máximo por archivo es %d. Reduce el rango de fechas.".formatted(rows, maxRows));
        }
        List<MovementLine> data = lines.findKardex(productId, warehouseId, range.from(), range.to(),
                PageRequest.of(0, maxRows)).getContent();
        MovementLineRepository.Totals totals = lines.totals(productId, warehouseId, range.from(), range.to());
        excelExporter.write(new KardexExcelExporter.Header(product, warehouse, from, to, COST_METHOD), data, totals, output);
    }

    private Range range(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("INVALID_RANGE", "La fecha inicial es posterior a la final");
        }
        return new Range(from == null ? DateRange.MIN : startOf(from),
                to == null ? DateRange.MAX : startOf(to.plusDays(1)));
    }

    private Instant startOf(LocalDate date) {
        return date.atStartOfDay(properties.inventory().timeZone()).toInstant();
    }

    private record Range(Instant from, Instant to) {
    }
}
