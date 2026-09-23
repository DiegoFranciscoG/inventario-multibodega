package io.github.diegofranciscog.inventory.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.diegofranciscog.inventory.config.AppProperties;
import io.github.diegofranciscog.inventory.dto.report.ReportResponses.ExpiringLotAlert;
import io.github.diegofranciscog.inventory.dto.report.ReportResponses.LowStockAlert;
import io.github.diegofranciscog.inventory.exception.BusinessRuleException;
import io.github.diegofranciscog.inventory.repository.StockRepository;

/** Alertas de stock mínimo por bodega y de lotes por vencer o vencidos con existencia (R-19). */
@Service
@Transactional(readOnly = true)
public class AlertService {

    private static final int MAX_DAYS = 365;

    private final StockRepository stocks;
    private final AppProperties properties;
    private final Clock clock;

    public AlertService(StockRepository stocks, AppProperties properties, Clock clock) {
        this.stocks = stocks;
        this.properties = properties;
        this.clock = clock;
    }

    public List<LowStockAlert> lowStock() {
        return stocks.findMinRuleStatus().stream()
                .filter(row -> row.currentQuantity().compareTo(row.minQuantity()) < 0)
                .map(row -> new LowStockAlert(row.productId(), row.sku(), row.productName(), row.warehouseId(),
                        row.warehouseCode(), row.minQuantity(), row.currentQuantity(),
                        row.minQuantity().subtract(row.currentQuantity())))
                .toList();
    }

    public List<ExpiringLotAlert> expiringLots(Integer days) {
        int window = days == null ? properties.inventory().expiringDays() : days;
        if (window < 0 || window > MAX_DAYS) {
            throw new BusinessRuleException("INVALID_RANGE", "Los días deben estar entre 0 y " + MAX_DAYS);
        }
        LocalDate today = today();
        return stocks.findExpiring(today.plusDays(window)).stream()
                .map(row -> new ExpiringLotAlert(row.lotId(), row.productId(), row.sku(), row.productName(),
                        row.lotNumber(), row.expiryDate(), ChronoUnit.DAYS.between(today, row.expiryDate()),
                        row.expiryDate().isBefore(today), row.warehouseId(), row.warehouseCode(), row.quantity()))
                .toList();
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(properties.inventory().timeZone()));
    }
}
