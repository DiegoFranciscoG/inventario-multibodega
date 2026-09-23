package io.github.diegofranciscog.inventory.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.github.diegofranciscog.inventory.exception.BusinessRuleException;

/** Costo promedio ponderado móvil (NIC 2 párr. 25–27): R-01 a R-04. */
class ProductCostTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    @Test
    void receiptsRecalculateTheWeightedAverage() {
        ProductCost cost = new ProductCost(1L);

        cost.receive(bd("100"), bd("2.00"), NOW);
        CostPosting second = cost.receive(bd("50"), bd("2.60"), NOW);

        // (100 × 2.00 + 50 × 2.60) / 150 = 330 / 150 = 2.20
        assertThat(second.averageCost()).isEqualByComparingTo("2.20");
        assertThat(second.balanceQuantity()).isEqualByComparingTo("150");
        assertThat(second.balanceValue()).isEqualByComparingTo("330");
        assertThat(second.totalCost()).isEqualByComparingTo("130");
    }

    @Test
    void issuesLeaveAtCurrentAverageWithoutChangingIt() {
        ProductCost cost = new ProductCost(1L);
        cost.receive(bd("100"), bd("2.00"), NOW);
        cost.receive(bd("50"), bd("2.60"), NOW);

        CostPosting issue = cost.issue(bd("30"), NOW);

        assertThat(issue.unitCost()).isEqualByComparingTo("2.20");
        assertThat(issue.totalCost()).isEqualByComparingTo("66");
        assertThat(issue.averageCost()).isEqualByComparingTo("2.20");
        assertThat(cost.getQuantityOnHand()).isEqualByComparingTo("120");
        assertThat(cost.getTotalValue()).isEqualByComparingTo("264");
    }

    @Test
    void emptyingTheProductTakesAllRemainingValueSoNoRoundingResidueStays() {
        ProductCost cost = new ProductCost(1L);
        cost.receive(bd("3"), bd("1.00"), NOW);
        cost.receive(bd("3"), bd("1.01"), NOW);   // promedio 1.005 → valor 6.03
        cost.issue(bd("1"), NOW);

        CostPosting last = cost.issue(bd("5"), NOW);

        assertThat(cost.getQuantityOnHand()).isEqualByComparingTo("0");
        assertThat(cost.getTotalValue()).isEqualByComparingTo("0");
        assertThat(last.balanceValue()).isEqualByComparingTo("0");
    }

    @Test
    void transferInRestoresTheSameValueWithoutRecalculating() {
        ProductCost cost = new ProductCost(1L);
        cost.receive(bd("10"), bd("3.333333"), NOW);
        BigDecimal averageBefore = cost.getAverageCost();

        CostPosting out = cost.issue(bd("4"), NOW);
        CostPosting in = cost.transferIn(bd("4"), out.unitCost(), out.totalCost(), NOW);

        assertThat(in.balanceQuantity()).isEqualByComparingTo("10");
        assertThat(in.balanceValue()).isEqualByComparingTo(cost.getTotalValue());
        assertThat(cost.getAverageCost()).isEqualByComparingTo(averageBefore);
    }

    @Test
    void rejectsNegativeCostAndNonPositiveQuantities() {
        ProductCost cost = new ProductCost(1L);
        assertThatThrownBy(() -> cost.receive(bd("1"), bd("-1"), NOW)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> cost.receive(bd("0"), bd("1"), NOW)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> cost.issue(bd("1"), NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("valorizada");
    }

    @Test
    void remembersTheLastMovementForChronology() {
        ProductCost cost = new ProductCost(7L);
        cost.receive(bd("1"), bd("1"), NOW);
        assertThat(cost.getLastMovementAt()).isEqualTo(NOW);
        assertThat(cost.getProductId()).isEqualTo(7L);
        assertThat(cost.getVersion()).isZero();
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
