package io.github.diegofranciscog.inventory.service;

import static io.github.diegofranciscog.inventory.support.TestData.line;
import static io.github.diegofranciscog.inventory.support.TestData.receipt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.AddLineRequest;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.CountLineRequest;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.CreateRequest;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.CycleCountResponse;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.LineResponse;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.ReviewRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.IssueLine;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.IssueRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementResponses.MovementResponse;
import io.github.diegofranciscog.inventory.entity.AppUser;
import io.github.diegofranciscog.inventory.entity.CycleCountStatus;
import io.github.diegofranciscog.inventory.entity.Location;
import io.github.diegofranciscog.inventory.entity.MovementType;
import io.github.diegofranciscog.inventory.entity.Product;
import io.github.diegofranciscog.inventory.entity.Role;
import io.github.diegofranciscog.inventory.entity.Warehouse;
import io.github.diegofranciscog.inventory.exception.BusinessRuleException;
import io.github.diegofranciscog.inventory.exception.ForbiddenOperationException;
import io.github.diegofranciscog.inventory.exception.InsufficientStockException;
import io.github.diegofranciscog.inventory.repository.StockRepository;
import io.github.diegofranciscog.inventory.support.AbstractIntegrationTest;

/** Lo destacado del proyecto: conteo cíclico con diferencias, ajuste aprobado por otra persona y auditado (R-20, R-21). */
class CycleCountIT extends AbstractIntegrationTest {

    @Autowired
    private CycleCountService counts;
    @Autowired
    private MovementService movements;
    @Autowired
    private MovementQueryService movementQueries;
    @Autowired
    private StockRepository stocks;

    private Warehouse quito;
    private Location aisle01;
    private Location aisle02;
    private AppUser operator;
    private AppUser supervisor;
    private Product soap;
    private Product gloves;

    @BeforeEach
    void setUp() {
        quito = data.warehouse("BOD-UIO");
        aisle01 = data.location(quito, "01", "01", "1");
        aisle02 = data.location(quito, "02", "01", "1");
        operator = data.user("operador@test.local", Role.OPERATOR, quito);
        supervisor = data.user("supervisor@test.local", Role.SUPERVISOR);
        soap = data.product("SOAP-1", false);
        gloves = data.product("GLOVES-1", false);
        movements.receive(receipt(quito, line(soap, aisle01, "10", "2.00"), line(gloves, aisle02, "5", "4.00")),
                supervisor.getId());
    }

    @Test
    void blindCountWithDifferencesIsApprovedByAnotherPersonAndCreatesAnAuditedAdjustment() {
        CycleCountResponse created = counts.create(new CreateRequest(quito.getId(), null, null, "semanal"), operator.getId());
        assertThat(created.blind()).isTrue();
        assertThat(created.lines()).hasSize(2).allSatisfy(line -> assertThat(line.systemQuantity()).isNull());

        countAll(created, "8", "5");
        CycleCountResponse submitted = counts.submit(created.id(), operator.getId());

        assertThat(submitted.blind()).isFalse();
        assertThat(submitted.linesWithDifference()).isEqualTo(1);
        assertThat(submitted.differenceValue()).isEqualByComparingTo("-4");
        LineResponse soapLine = lineOf(submitted, "SOAP-1");
        assertThat(soapLine.systemQuantity()).isEqualByComparingTo("10");
        assertThat(soapLine.difference()).isEqualByComparingTo("-2");

        assertThatThrownBy(() -> counts.approve(created.id(), null, operator.getId()))
                .isInstanceOfSatisfying(ForbiddenOperationException.class,
                        e -> assertThat(e.getCode()).isEqualTo("SEGREGATION_OF_DUTIES"));

        CycleCountResponse approved = counts.approve(created.id(), new ReviewRequest("Diferencia validada"), supervisor.getId());

        assertThat(approved.status()).isEqualTo(CycleCountStatus.APPROVED);
        assertThat(approved.reviewedBy()).isEqualTo("supervisor@test.local");
        assertThat(approved.adjustmentMovementNumber()).startsWith("AJU-");
        assertThat(stocks.sumByProductAndWarehouse(soap.getId(), quito.getId())).isEqualByComparingTo("8");

        Long adjustmentId = jdbc.queryForObject("select id from inventory_movement where number = ?", Long.class,
                approved.adjustmentMovementNumber());
        MovementResponse adjustment = movementQueries.get(adjustmentId);
        assertThat(adjustment.type()).isEqualTo(MovementType.ADJUSTMENT);
        assertThat(adjustment.cycleCountId()).isEqualTo(created.id());
        assertThat(adjustment.referenceTypeCode()).isEqualTo("CC");
        assertThat(adjustment.totalCost()).isEqualByComparingTo("-4");

        List<String> audit = jdbc.queryForList(
                "select action from audit_log where entity_type = 'CycleCount' order by id", String.class);
        assertThat(audit).containsExactly("COUNT_CREATED", "COUNT_SUBMITTED", "COUNT_APPROVED");
        assertKardexBalanced();
    }

    @Test
    void aSupervisorWhoCountedCannotApproveTheirOwnCount() {
        CycleCountResponse created = counts.create(new CreateRequest(quito.getId(), "02", null, null), supervisor.getId());
        assertThat(created.lines()).singleElement().satisfies(line -> assertThat(line.sku()).isEqualTo("GLOVES-1"));
        countAll(created, "4");
        counts.submit(created.id(), supervisor.getId());

        assertThatThrownBy(() -> counts.approve(created.id(), null, supervisor.getId()))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThat(counts.get(created.id()).status()).isEqualTo(CycleCountStatus.SUBMITTED);
    }

    @Test
    void approvalFailsSafelyIfStockWasIssuedAfterCounting() {
        CycleCountResponse created = counts.create(new CreateRequest(quito.getId(), "01", null, null), operator.getId());
        countAll(created, "3");
        counts.submit(created.id(), operator.getId());
        movements.issue(new IssueRequest(quito.getId(), "PED", "PED-9", null,
                List.of(new IssueLine(soap.getId(), new BigDecimal("9"), null, null, null))), supervisor.getId());

        assertThatThrownBy(() -> counts.approve(created.id(), null, supervisor.getId()))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(counts.get(created.id()).status()).isEqualTo(CycleCountStatus.SUBMITTED);
        assertThat(stocks.sumByProductAndWarehouse(soap.getId(), quito.getId())).isEqualByComparingTo("1");
        assertKardexBalanced();
    }

    @Test
    void productsFoundInUnexpectedLocationsBecomePositiveAdjustments() {
        CycleCountResponse created = counts.create(new CreateRequest(quito.getId(), "01", null, null), operator.getId());
        countAll(created, "10");
        CycleCountResponse withExtra = counts.addLine(created.id(),
                new AddLineRequest(aisle01.getId(), gloves.getId(), null, null, new BigDecimal("2"), "encontrado"),
                operator.getId());
        assertThat(withExtra.totalLines()).isEqualTo(2);
        counts.submit(created.id(), operator.getId());

        CycleCountResponse approved = counts.approve(created.id(), null, supervisor.getId());

        assertThat(approved.differenceValue()).isEqualByComparingTo("8");
        assertThat(stocks.sumByProductAndWarehouse(gloves.getId(), quito.getId())).isEqualByComparingTo("7");
        assertKardexBalanced();
    }

    @Test
    void rejectedAndCancelledCountsDoNotTouchStock() {
        CycleCountResponse first = counts.create(new CreateRequest(quito.getId(), "01", null, null), operator.getId());
        assertThatThrownBy(() -> counts.submit(first.id(), operator.getId()))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("COUNT_INCOMPLETE"));
        countAll(first, "1");
        counts.submit(first.id(), operator.getId());
        assertThat(counts.reject(first.id(), new ReviewRequest("recontar"), supervisor.getId()).status())
                .isEqualTo(CycleCountStatus.REJECTED);

        CycleCountResponse second = counts.create(new CreateRequest(quito.getId(), "01", null, null), operator.getId());
        assertThat(counts.cancel(second.id(), supervisor.getId()).status()).isEqualTo(CycleCountStatus.CANCELLED);

        assertThat(stocks.sumByProductAndWarehouse(soap.getId(), quito.getId())).isEqualByComparingTo("10");
        assertThat(counts.search(quito.getId(), null, 0, 10).totalElements()).isEqualTo(2);
        assertThatThrownBy(() -> counts.create(new CreateRequest(quito.getId(), "99", null, null), operator.getId()))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("NOTHING_TO_COUNT"));
        assertKardexBalanced();
    }

    private void countAll(CycleCountResponse count, String... quantities) {
        for (int i = 0; i < count.lines().size(); i++) {
            counts.registerCount(count.id(), count.lines().get(i).id(),
                    new CountLineRequest(new BigDecimal(quantities[i]), null), count.createdBy().equals(operator.getEmail())
                            ? operator.getId() : supervisor.getId());
        }
    }

    private static LineResponse lineOf(CycleCountResponse count, String sku) {
        return count.lines().stream().filter(line -> line.sku().equals(sku)).findFirst().orElseThrow();
    }
}
