package io.github.diegofranciscog.inventory.exception;

import java.math.BigDecimal;

/** Se intentó sacar más de lo disponible (R-07). */
public class InsufficientStockException extends BusinessRuleException {

    private final BigDecimal available;
    private final BigDecimal requested;

    public InsufficientStockException(String sku, String where, BigDecimal available, BigDecimal requested) {
        super("INSUFFICIENT_STOCK", "Stock insuficiente de %s en %s: disponible %s, solicitado %s"
                .formatted(sku, where, available.stripTrailingZeros().toPlainString(),
                        requested.stripTrailingZeros().toPlainString()));
        this.available = available;
        this.requested = requested;
    }

    public BigDecimal getAvailable() {
        return available;
    }

    public BigDecimal getRequested() {
        return requested;
    }
}
