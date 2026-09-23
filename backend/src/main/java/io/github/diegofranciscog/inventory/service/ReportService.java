package io.github.diegofranciscog.inventory.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.diegofranciscog.inventory.dto.report.ReportResponses.DashboardResponse;
import io.github.diegofranciscog.inventory.dto.report.ReportResponses.ExpiringLotAlert;
import io.github.diegofranciscog.inventory.dto.report.ReportResponses.ReconciliationResponse;
import io.github.diegofranciscog.inventory.dto.report.ReportResponses.ReconciliationRow;
import io.github.diegofranciscog.inventory.entity.CycleCountStatus;
import io.github.diegofranciscog.inventory.repository.CycleCountRepository;
import io.github.diegofranciscog.inventory.repository.InventoryMovementRepository;
import io.github.diegofranciscog.inventory.repository.ProductCostRepository;
import io.github.diegofranciscog.inventory.repository.ProductRepository;
import io.github.diegofranciscog.inventory.repository.WarehouseRepository;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final JdbcClient jdbc;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final ProductCostRepository costs;
    private final InventoryMovementRepository movements;
    private final CycleCountRepository counts;
    private final AlertService alerts;
    private final Clock clock;

    public ReportService(JdbcClient jdbc, ProductRepository products, WarehouseRepository warehouses,
                         ProductCostRepository costs, InventoryMovementRepository movements, CycleCountRepository counts,
                         AlertService alerts, Clock clock) {
        this.jdbc = jdbc;
        this.products = products;
        this.warehouses = warehouses;
        this.costs = costs;
        this.movements = movements;
        this.counts = counts;
        this.alerts = alerts;
        this.clock = clock;
    }

    /**
     * R-09: por producto deben coincidir Σ stock por ubicación, cantidad valorizada, Σ(entradas − salidas) del kárdex y el
     * saldo del último renglón; y el valor del costo promedio con Σ(valor entradas − valor salidas).
     */
    public ReconciliationResponse reconciliation() {
        List<ReconciliationRow> rows = jdbc.sql("""
                        select product_id, sku, product_name, stock_quantity, valuation_quantity, kardex_quantity,
                               last_balance_quantity, valuation_value, kardex_value
                        from v_stock_reconciliation
                        order by sku
                        """)
                .query((rs, rowNum) -> row(
                        rs.getLong("product_id"), rs.getString("sku"), rs.getString("product_name"),
                        rs.getBigDecimal("stock_quantity"), rs.getBigDecimal("valuation_quantity"),
                        rs.getBigDecimal("kardex_quantity"), rs.getBigDecimal("last_balance_quantity"),
                        rs.getBigDecimal("valuation_value"), rs.getBigDecimal("kardex_value")))
                .list();
        long unbalanced = rows.stream().filter(row -> !row.balanced()).count();
        return new ReconciliationResponse(unbalanced == 0, rows.size(), unbalanced, Instant.now(clock), rows);
    }

    public DashboardResponse dashboard() {
        List<ExpiringLotAlert> expiring = alerts.expiringLots(null);
        long expired = expiring.stream().filter(ExpiringLotAlert::expired).count();
        BigDecimal inventoryValue = costs.totalInventoryValue();
        return new DashboardResponse(
                products.countByActiveTrue(),
                warehouses.countByActiveTrue(),
                inventoryValue,
                alerts.lowStock().size(),
                expiring.size() - expired,
                expired,
                counts.countByStatus(CycleCountStatus.SUBMITTED),
                movements.countByOccurredAtGreaterThanEqual(Instant.now(clock).minus(Duration.ofDays(7))),
                reconciliation().balanced());
    }

    static ReconciliationRow row(Long productId, String sku, String name, BigDecimal stock, BigDecimal valuation,
                                 BigDecimal kardex, BigDecimal lastBalance, BigDecimal valuationValue, BigDecimal kardexValue) {
        boolean balanced = stock.compareTo(valuation) == 0
                && stock.compareTo(kardex) == 0
                && stock.compareTo(lastBalance) == 0
                && valuationValue.compareTo(kardexValue) == 0;
        return new ReconciliationRow(productId, sku, name, stock, valuation, kardex, lastBalance, valuationValue,
                kardexValue, balanced);
    }
}
