package io.github.diegofranciscog.inventory.service;

import static io.github.diegofranciscog.inventory.support.TestData.line;
import static io.github.diegofranciscog.inventory.support.TestData.lotLine;
import static io.github.diegofranciscog.inventory.support.TestData.receipt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.diegofranciscog.inventory.dto.catalog.ProductRequest;
import io.github.diegofranciscog.inventory.dto.kardex.KardexResponses.KardexEntry;
import io.github.diegofranciscog.inventory.dto.kardex.KardexResponses.KardexResponse;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.AdjustmentLine;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.AdjustmentRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.IssueLine;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.IssueRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.ReceiptLine;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.TransferLine;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.TransferRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementResponses.MovementLineResponse;
import io.github.diegofranciscog.inventory.dto.movement.MovementResponses.MovementResponse;
import io.github.diegofranciscog.inventory.entity.AppUser;
import io.github.diegofranciscog.inventory.entity.Direction;
import io.github.diegofranciscog.inventory.entity.Location;
import io.github.diegofranciscog.inventory.entity.Lot;
import io.github.diegofranciscog.inventory.entity.Product;
import io.github.diegofranciscog.inventory.entity.ProductCost;
import io.github.diegofranciscog.inventory.entity.Role;
import io.github.diegofranciscog.inventory.entity.Warehouse;
import io.github.diegofranciscog.inventory.exception.BusinessRuleException;
import io.github.diegofranciscog.inventory.exception.ForbiddenOperationException;
import io.github.diegofranciscog.inventory.exception.InsufficientStockException;
import io.github.diegofranciscog.inventory.repository.InventoryMovementRepository;
import io.github.diegofranciscog.inventory.repository.LotRepository;
import io.github.diegofranciscog.inventory.repository.MovementLineRepository;
import io.github.diegofranciscog.inventory.repository.ProductCostRepository;
import io.github.diegofranciscog.inventory.repository.StockRepository;
import io.github.diegofranciscog.inventory.support.AbstractIntegrationTest;

class MovementServiceIT extends AbstractIntegrationTest {

    private static final ZoneId ECUADOR = ZoneId.of("America/Guayaquil");

    @Autowired
    private MovementService movements;
    @Autowired
    private MovementPostingService posting;
    @Autowired
    private KardexService kardex;
    @Autowired
    private StockRepository stocks;
    @Autowired
    private ProductCostRepository costs;
    @Autowired
    private LotRepository lots;
    @Autowired
    private InventoryMovementRepository movementRepository;
    @Autowired
    private MovementLineRepository lineRepository;
    @Autowired
    private Clock clock;

    private Warehouse quito;
    private Warehouse guayaquil;
    private Location quitoA;
    private Location quitoB;
    private Location guayaquilA;
    private AppUser supervisor;
    private Product soap;
    private Product rice;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        quito = data.warehouse("BOD-UIO");
        guayaquil = data.warehouse("BOD-GYE");
        quitoA = data.location(quito, "01", "01", "1");
        quitoB = data.location(quito, "01", "01", "2");
        guayaquilA = data.location(guayaquil, "01", "01", "1");
        supervisor = data.user("supervisor@test.local", Role.SUPERVISOR);
        soap = data.product("SOAP-1", false);
        rice = data.product("RICE-1", true);
        today = LocalDate.now(clock.withZone(ECUADOR));
    }

    @Test
    void receiptsRecalculateWeightedAverageAndIssuesLeaveAtThatCost() {
        movements.receive(receipt(quito, line(soap, quitoA, "100", "2.00")), supervisor.getId());
        movements.receive(receipt(quito, line(soap, quitoA, "50", "2.60")), supervisor.getId());

        MovementResponse issue = movements.issue(issue(quito, new IssueLine(soap.getId(), bd("30"), null, null, null)),
                supervisor.getId());

        MovementLineResponse issued = issue.lines().getFirst();
        assertThat(issued.unitCost()).isEqualByComparingTo("2.20");
        assertThat(issued.totalCost()).isEqualByComparingTo("66");
        assertThat(issue.number()).startsWith("EGR-");
        ProductCost cost = costs.findById(soap.getId()).orElseThrow();
        assertThat(cost.getAverageCost()).isEqualByComparingTo("2.20");
        assertThat(cost.getQuantityOnHand()).isEqualByComparingTo("120");
        assertThat(cost.getTotalValue()).isEqualByComparingTo("264");

        List<KardexEntry> entries = kardex.kardex(soap.getId(), null, null, null, 0, 50).entries().content();
        assertThat(entries).extracting(KardexEntry::balanceQuantity)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(bd("100"), bd("150"), bd("120"));
        assertThat(entries).extracting(KardexEntry::balanceValue)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(bd("200"), bd("330"), bd("264"));
        assertKardexBalanced();
    }

    @Test
    void anIssueLargerThanTheStockIsRejectedAndNothingIsApplied() {
        movements.receive(receipt(quito, line(soap, quitoA, "5", "1.00")), supervisor.getId());

        assertThatThrownBy(() -> movements.issue(issue(quito,
                new IssueLine(soap.getId(), bd("3"), null, null, null),
                new IssueLine(soap.getId(), bd("5"), null, null, null)), supervisor.getId()))
                .isInstanceOfSatisfying(InsufficientStockException.class, e -> {
                    assertThat(e.getAvailable()).isEqualByComparingTo("2");
                    assertThat(e.getRequested()).isEqualByComparingTo("5");
                });

        assertThat(stocks.sumByProductAndWarehouse(soap.getId(), quito.getId())).isEqualByComparingTo("5");
        assertThat(movementRepository.count()).isEqualTo(1);
        assertThat(lineRepository.count()).isEqualTo(1);
        assertKardexBalanced();
    }

    @Test
    void fefoTakesTheEarliestExpiryFirstAndNeverAnExpiredLot() {
        movements.receive(receipt(quito,
                lotLine(rice, quitoA, "10", "1.00", "A", today.plusDays(10)),
                lotLine(rice, quitoB, "10", "1.00", "B", today.plusDays(100)),
                lotLine(rice, quitoA, "10", "1.00", "X", today.minusDays(1))), supervisor.getId());

        MovementResponse issue = movements.issue(issue(quito, new IssueLine(rice.getId(), bd("15"), null, null, null)),
                supervisor.getId());

        assertThat(issue.lines()).extracting(MovementLineResponse::lotNumber).containsExactly("A", "B");
        assertThat(issue.lines()).extracting(MovementLineResponse::quantity)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(bd("10"), bd("5"));

        Lot expired = lots.findByProductIdAndLotNumber(rice.getId(), "X").orElseThrow();
        assertThatThrownBy(() -> movements.issue(issue(quito,
                new IssueLine(rice.getId(), bd("1"), null, null, expired.getId())), supervisor.getId()))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("LOT_EXPIRED"));

        assertThatThrownBy(() -> movements.issue(issue(quito, new IssueLine(rice.getId(), bd("6"), null, null, null)),
                supervisor.getId()))
                .as("solo quedan 5 no vencidas")
                .isInstanceOf(InsufficientStockException.class);
        assertKardexBalanced();
    }

    @Test
    void transferMovesStockBetweenWarehousesAtTheSameCost() {
        movements.receive(receipt(quito, lotLine(rice, quitoA, "20", "1.50", "A", today.plusDays(90))), supervisor.getId());

        MovementResponse transfer = movements.transfer(transfer(quito, guayaquil,
                new TransferLine(rice.getId(), bd("8"), null, null, null, guayaquilA.getId())), supervisor.getId());

        assertThat(transfer.lines()).extracting(MovementLineResponse::direction)
                .containsExactly(Direction.OUT, Direction.IN);
        assertThat(transfer.lines()).extracting(MovementLineResponse::lotNumber).containsOnly("A");
        assertThat(transfer.totalCost()).isEqualByComparingTo("12");
        assertThat(stocks.sumByProductAndWarehouse(rice.getId(), quito.getId())).isEqualByComparingTo("12");
        assertThat(stocks.sumByProductAndWarehouse(rice.getId(), guayaquil.getId())).isEqualByComparingTo("8");
        ProductCost cost = costs.findById(rice.getId()).orElseThrow();
        assertThat(cost.getAverageCost()).isEqualByComparingTo("1.50");
        assertThat(cost.getQuantityOnHand()).isEqualByComparingTo("20");

        List<KardexEntry> guayaquilKardex = kardex.kardex(rice.getId(), guayaquil.getId(), null, null, 0, 50)
                .entries().content();
        assertThat(guayaquilKardex).singleElement()
                .satisfies(entry -> assertThat(entry.warehouseBalanceQuantity()).isEqualByComparingTo("8"));
        assertKardexBalanced();
    }

    @Test
    void aTransferIsASingleTransactionEvenWithSeveralLines() {
        movements.receive(receipt(quito,
                line(soap, quitoA, "5", "1.00"),
                lotLine(rice, quitoA, "3", "1.00", "A", today.plusDays(30))), supervisor.getId());

        assertThatThrownBy(() -> movements.transfer(transfer(quito, guayaquil,
                new TransferLine(soap.getId(), bd("5"), null, null, null, guayaquilA.getId()),
                new TransferLine(rice.getId(), bd("10"), null, null, null, guayaquilA.getId())), supervisor.getId()))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(stocks.sumByProductAndWarehouse(soap.getId(), quito.getId())).isEqualByComparingTo("5");
        assertThat(stocks.sumByProductAndWarehouse(soap.getId(), guayaquil.getId())).isEqualByComparingTo("0");
        assertThat(movementRepository.count()).isEqualTo(1);
        assertKardexBalanced();
    }

    @Test
    void anOperatorOnlyOperatesInAssignedWarehouses() {
        AppUser operator = data.user("operador@test.local", Role.OPERATOR, quito);
        movements.receive(receipt(quito, line(soap, quitoA, "4", "1.00")), operator.getId());

        assertThatThrownBy(() -> movements.transfer(transfer(quito, guayaquil,
                new TransferLine(soap.getId(), bd("1"), null, null, null, guayaquilA.getId())), operator.getId()))
                .isInstanceOfSatisfying(ForbiddenOperationException.class,
                        e -> assertThat(e.getCode()).isEqualTo("WAREHOUSE_FORBIDDEN"));
        assertThatThrownBy(() -> movements.receive(receipt(guayaquil, line(soap, guayaquilA, "1", "1")), operator.getId()))
                .isInstanceOf(ForbiddenOperationException.class);
        assertKardexBalanced();
    }

    @Test
    void packagingUnitsAreConvertedToTheBaseUnit() {
        Product water = data.product("WATER-600", false,
                new ProductRequest.Conversion("XBX", bd("24"), "19520000000018"));

        MovementResponse receipt = movements.receive(receipt(quito, new ReceiptLine(water.getId(), quitoA.getId(), bd("2"),
                "XBX", bd("12.00"), null, null, null)), supervisor.getId());

        MovementLineResponse line = receipt.lines().getFirst();
        assertThat(line.quantity()).isEqualByComparingTo("48");
        assertThat(line.uomCode()).isEqualTo("XBX");
        assertThat(line.uomQuantity()).isEqualByComparingTo("2");
        assertThat(line.unitCost()).isEqualByComparingTo("0.50");

        assertThatThrownBy(() -> movements.receive(receipt(quito, new ReceiptLine(water.getId(), quitoA.getId(), bd("1"),
                "DZN", bd("1"), null, null, null)), supervisor.getId()))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("UNKNOWN_UOM"));
        assertKardexBalanced();
    }

    @Test
    void adjustmentsUseAverageCostAndCannotCreateNegativeStock() {
        AdjustmentRequest withoutCost = adjustment(new AdjustmentLine(soap.getId(), quitoA.getId(), null, null, null,
                bd("2"), null));
        assertThatThrownBy(() -> movements.adjust(withoutCost, supervisor.getId()))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("COST_REQUIRED"));

        movements.adjust(adjustment(new AdjustmentLine(soap.getId(), quitoA.getId(), null, null, null, bd("4"),
                bd("3.00"))), supervisor.getId());
        MovementResponse negative = movements.adjust(adjustment(new AdjustmentLine(soap.getId(), quitoA.getId(), null,
                null, null, bd("-1"), null)), supervisor.getId());

        assertThat(negative.lines().getFirst().unitCost()).isEqualByComparingTo("3.00");
        assertThat(negative.totalCost()).isEqualByComparingTo("-3");
        assertThatThrownBy(() -> movements.adjust(adjustment(new AdjustmentLine(soap.getId(), quitoA.getId(), null, null,
                null, bd("-10"), null)), supervisor.getId()))
                .isInstanceOf(InsufficientStockException.class);
        assertThatThrownBy(() -> movements.adjust(adjustment(new AdjustmentLine(soap.getId(), quitoA.getId(), null, null,
                null, bd("0"), null)), supervisor.getId()))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("ZERO_ADJUSTMENT"));
        assertKardexBalanced();
    }

    @Test
    void lotControlledProductsRequireLotAndExpiry() {
        assertCode(() -> movements.receive(receipt(quito, line(rice, quitoA, "1", "1")), supervisor.getId()), "LOT_REQUIRED");
        assertCode(() -> movements.receive(receipt(quito, lotLine(rice, quitoA, "1", "1", "L1", null)), supervisor.getId()),
                "EXPIRY_REQUIRED");
        assertCode(() -> movements.receive(receipt(quito, lotLine(soap, quitoA, "1", "1", "L1", today)), supervisor.getId()),
                "LOT_NOT_ALLOWED");

        movements.receive(receipt(quito, lotLine(rice, quitoA, "1", "1", "L1", today.plusDays(5))), supervisor.getId());
        assertCode(() -> movements.receive(receipt(quito, lotLine(rice, quitoA, "1", "1", "L1", today.plusDays(6))),
                supervisor.getId()), "LOT_EXPIRY_MISMATCH");
        assertCode(() -> movements.receive(receipt(quito, line(soap, guayaquilA, "1", "1")), supervisor.getId()),
                "LOCATION_NOT_IN_WAREHOUSE");
        assertKardexBalanced();
    }

    @Test
    void movementsCannotBeBackdatedBeforeTheLastMovementOfTheProduct() {
        Instant now = Instant.now(clock);
        posting.receive(receipt(quito, line(soap, quitoA, "5", "1")), supervisor.getId(), now);

        assertCode(() -> posting.issue(issue(quito, new IssueLine(soap.getId(), bd("1"), null, null, null)),
                supervisor.getId(), now.minus(Duration.ofDays(1))), "BACKDATED_MOVEMENT");
    }

    @Test
    void kardexExportContainsEveryLineAndTheTotals() throws Exception {
        movements.receive(receipt(quito, line(soap, quitoA, "10", "2")), supervisor.getId());
        movements.issue(issue(quito, new IssueLine(soap.getId(), bd("4"), null, null, null)), supervisor.getId());
        movements.transfer(transfer(quito, guayaquil,
                new TransferLine(soap.getId(), bd("3"), null, null, null, guayaquilA.getId())), supervisor.getId());

        KardexResponse response = kardex.kardex(soap.getId(), null, today.minusDays(1), today, 0, 50);
        assertThat(response.entries().totalElements()).isEqualTo(4);
        assertThat(response.totals().inQuantity()).isEqualByComparingTo("13");
        assertThat(response.totals().outQuantity()).isEqualByComparingTo("7");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        kardex.exportXlsx(soap.getId(), null, null, null, output);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            Sheet sheet = workbook.getSheet("Kardex");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("SOAP-1");
            assertThat(sheet.getRow(6).getCell(0).getStringCellValue()).isEqualTo("Fecha");
            assertThat(sheet.getRow(7).getCell(1).getStringCellValue()).startsWith("ING-");
            assertThat(sheet.getRow(11).getCell(0).getStringCellValue()).isEqualTo("Totales del período");
            assertThat(sheet.getRow(11).getCell(7).getNumericCellValue()).isEqualTo(13.0);
            assertThat(sheet.getRow(10).getCell(13).getNumericCellValue()).isEqualTo(6.0);
        }

        assertCode(() -> kardex.kardex(soap.getId(), null, today, today.minusDays(1), 0, 10), "INVALID_RANGE");
        assertKardexBalanced();
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    private static IssueRequest issue(Warehouse warehouse, IssueLine... lines) {
        return new IssueRequest(warehouse.getId(), "PED", "PED-1", null, List.of(lines));
    }

    private static TransferRequest transfer(Warehouse source, Warehouse target, TransferLine... lines) {
        return new TransferRequest(source.getId(), target.getId(), "OT", "OT-1", null, List.of(lines));
    }

    private AdjustmentRequest adjustment(AdjustmentLine... lines) {
        return new AdjustmentRequest(quito.getId(), "AJ", "AJ-1", "Prueba", List.of(lines));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
