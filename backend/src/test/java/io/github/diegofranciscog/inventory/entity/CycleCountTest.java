package io.github.diegofranciscog.inventory.entity;

import static io.github.diegofranciscog.inventory.entity.DomainFixtures.location;
import static io.github.diegofranciscog.inventory.entity.DomainFixtures.product;
import static io.github.diegofranciscog.inventory.entity.DomainFixtures.user;
import static io.github.diegofranciscog.inventory.entity.DomainFixtures.warehouse;
import static io.github.diegofranciscog.inventory.entity.DomainFixtures.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.diegofranciscog.inventory.exception.BusinessRuleException;
import io.github.diegofranciscog.inventory.exception.ForbiddenOperationException;

/** Máquina de estados y segregación de funciones del conteo cíclico (R-20, R-21). */
class CycleCountTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    private final AppUser counter = user(1, Role.OPERATOR);
    private final AppUser supervisor = user(2, Role.SUPERVISOR);
    private CycleCount count;
    private CycleCountLine line;

    @BeforeEach
    void setUp() {
        Warehouse warehouse = warehouse(1, "BOD-UIO");
        count = new CycleCount("CC-000001", warehouse, null, null, null, supervisor, NOW);
        line = withId(count.addLine(location(1, warehouse), product(1, "SKU-1", false), null), 10L);
    }

    @Test
    void differenceIsCountedMinusSystemAndValuedAtAverageCost() {
        line.registerCount(new BigDecimal("8"), new BigDecimal("10"), new BigDecimal("2.5"), counter, NOW, "caja rota");

        assertThat(line.getDifference()).isEqualByComparingTo("-2");
        assertThat(line.differenceValue()).isEqualByComparingTo("-5");
        assertThat(line.hasDifference()).isTrue();
        assertThat(count.hasDifferences()).isTrue();
    }

    @Test
    void cannotSubmitWithPendingLines() {
        assertThatThrownBy(() -> count.submit(counter, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Faltan 1 líneas");
    }

    @Test
    void whoeverCountedCannotApprove() {
        line.registerCount(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, counter, NOW, null);
        count.submit(counter, NOW);

        assertThatThrownBy(() -> count.approve(counter, NOW, null, null))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("no puede aprobarlo");
        assertThatThrownBy(() -> count.reject(counter, NOW, null)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void supervisorApprovesSubmittedCount() {
        line.registerCount(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, counter, NOW, null);
        count.submit(counter, NOW);

        count.approve(supervisor, NOW, "ok", null);

        assertThat(count.getStatus()).isEqualTo(CycleCountStatus.APPROVED);
        assertThat(count.getReviewedBy()).isSameAs(supervisor);
        assertThat(count.hasDifferences()).isFalse();
        assertThatThrownBy(() -> count.cancel()).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void closedCountsRejectNewCounts() {
        count.cancel();
        assertThat(count.getStatus()).isEqualTo(CycleCountStatus.CANCELLED);
        assertThatThrownBy(() -> line.registerCount(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, counter, NOW, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsDuplicateLinesAndNegativeCounts() {
        Warehouse warehouse = count.getWarehouse();
        assertThatThrownBy(() -> count.addLine(location(1, warehouse), product(1, "SKU-1", false), null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> line.registerCount(new BigDecimal("-1"), BigDecimal.ONE, BigDecimal.ONE, counter, NOW, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectionKeepsTheReviewer() {
        line.registerCount(BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, counter, NOW, null);
        count.submit(counter, NOW);
        count.reject(supervisor, NOW, "recontar pasillo");
        assertThat(count.getStatus()).isEqualTo(CycleCountStatus.REJECTED);
        assertThat(count.getReviewNotes()).isEqualTo("recontar pasillo");
    }
}
