package io.github.diegofranciscog.inventory.dto.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.github.diegofranciscog.inventory.dto.catalog.CatalogResponses.ProductResponse;
import io.github.diegofranciscog.inventory.dto.storage.StorageResponses.LocationResponse;
import io.github.diegofranciscog.inventory.dto.storage.StorageResponses.StockResponse;

/** Reportes, alertas, escaneo y auditoría. */
public final class ReportResponses {

    private ReportResponses() {
    }

    public record ReconciliationRow(
            Long productId,
            String sku,
            String productName,
            BigDecimal stockQuantity,
            BigDecimal valuationQuantity,
            BigDecimal kardexQuantity,
            BigDecimal lastBalanceQuantity,
            BigDecimal valuationValue,
            BigDecimal kardexValue,
            boolean balanced) {
    }

    public record ReconciliationResponse(boolean balanced, int productsChecked, long unbalancedProducts,
                                         Instant checkedAt, List<ReconciliationRow> rows) {
    }

    public record LowStockAlert(Long productId, String sku, String productName, Long warehouseId, String warehouseCode,
                                BigDecimal minQuantity, BigDecimal currentQuantity, BigDecimal shortage) {
    }

    public record ExpiringLotAlert(Long lotId, Long productId, String sku, String productName, String lotNumber,
                                   LocalDate expiryDate, long daysToExpiry, boolean expired, Long warehouseId,
                                   String warehouseCode, BigDecimal quantity) {
    }

    public record DashboardResponse(
            long products,
            long warehouses,
            BigDecimal inventoryValue,
            long lowStockAlerts,
            long expiringLots,
            long expiredLots,
            long countsPendingApproval,
            long movementsLast7Days,
            boolean kardexBalanced) {
    }

    public enum ScanType { PRODUCT, LOCATION, UNKNOWN }

    public enum ScanFormat { GTIN, GS1_ELEMENT_STRING, GS1_DIGITAL_LINK, LOCATION_QR, SKU, UNKNOWN }

    public record ScanResponse(
            ScanType type,
            ScanFormat format,
            String code,
            ProductResponse product,
            String uomCode,
            BigDecimal factor,
            String lotNumber,
            LocalDate expiryDate,
            Long lotId,
            LocationResponse location,
            List<StockResponse> stock) {
    }

    public record AuditLogResponse(Long id, Instant occurredAt, String actorEmail, String action, String entityType,
                                   String entityId, Map<String, Object> details) {
    }
}
