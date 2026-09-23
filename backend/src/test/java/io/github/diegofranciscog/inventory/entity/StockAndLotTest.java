package io.github.diegofranciscog.inventory.entity;

import static io.github.diegofranciscog.inventory.entity.DomainFixtures.location;
import static io.github.diegofranciscog.inventory.entity.DomainFixtures.product;
import static io.github.diegofranciscog.inventory.entity.DomainFixtures.warehouse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import io.github.diegofranciscog.inventory.exception.InsufficientStockException;

class StockAndLotTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    @Test
    void stockNeverGoesNegative() {
        Warehouse warehouse = warehouse(1, "BOD-UIO");
        Stock stock = new Stock(product(1, "SKU-1", false), warehouse, location(1, warehouse), null);
        stock.increase(new BigDecimal("5"), NOW);

        assertThatThrownBy(() -> stock.decrease(new BigDecimal("6"), NOW))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("disponible 5, solicitado 6");
        assertThat(stock.getQuantity()).isEqualByComparingTo("5");

        stock.decrease(new BigDecimal("5"), NOW);
        assertThat(stock.getQuantity()).isEqualByComparingTo("0");
        assertThat(stock.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsZeroOrNegativeAmounts() {
        Warehouse warehouse = warehouse(1, "BOD-UIO");
        Stock stock = new Stock(product(1, "SKU-1", false), warehouse, location(1, warehouse), null);
        assertThatThrownBy(() -> stock.increase(BigDecimal.ZERO, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stock.decrease(new BigDecimal("-1"), NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lotIsExpiredOnlyAfterItsExpiryDate() {
        Lot lot = new Lot(product(1, "SKU-1", true), "L1", LocalDate.of(2026, 9, 1), null);
        assertThat(lot.isExpiredOn(LocalDate.of(2026, 9, 1))).isFalse();
        assertThat(lot.isExpiredOn(LocalDate.of(2026, 9, 2))).isTrue();
        assertThat(new Lot(product(1, "SKU-1", true), "L2", null, null).isExpiredOn(LocalDate.MAX)).isFalse();
    }

    @Test
    void locationCodeAndQrFollowTheHierarchy() {
        Location location = new Location(warehouse(1, "BOD-UIO"), "01", "02", "3", LocationType.STORAGE);
        assertThat(location.getCode()).isEqualTo("P01-E02-N3");
        assertThat(location.qrPayload()).isEqualTo("LOC:BOD-UIO:P01-E02-N3");
    }

    @Test
    void movementNumbersUseTypePrefixes() {
        assertThat(MovementType.RECEIPT.formatNumber(7)).isEqualTo("ING-000007");
        assertThat(MovementType.TRANSFER.formatNumber(123456)).isEqualTo("TRF-123456");
    }

    @Test
    void productConversionFactors() {
        Product product = product(1, "SKU-1", false);
        assertThat(product.factorFor(null)).contains(BigDecimal.ONE);
        assertThat(product.factorFor("H87")).contains(BigDecimal.ONE);
        assertThat(product.factorFor("XBX")).isEmpty();
    }

    @Test
    void operatorsOnlyOperateInAssignedWarehouses() {
        Warehouse quito = warehouse(1, "BOD-UIO");
        AppUser operator = DomainFixtures.user(1, Role.OPERATOR);
        operator.assignWarehouses(java.util.Set.of(quito));
        assertThat(operator.canOperateIn(1L)).isTrue();
        assertThat(operator.canOperateIn(2L)).isFalse();
        assertThat(DomainFixtures.user(2, Role.SUPERVISOR).canOperateIn(2L)).isTrue();
        assertThat(AppUser.normalizeEmail("  Ana@Demo.LOCAL ")).isEqualTo("ana@demo.local");
    }
}
