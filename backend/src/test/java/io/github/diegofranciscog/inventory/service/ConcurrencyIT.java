package io.github.diegofranciscog.inventory.service;

import static io.github.diegofranciscog.inventory.support.TestData.line;
import static io.github.diegofranciscog.inventory.support.TestData.receipt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.IssueLine;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.IssueRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.TransferLine;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.TransferRequest;
import io.github.diegofranciscog.inventory.entity.AppUser;
import io.github.diegofranciscog.inventory.entity.Direction;
import io.github.diegofranciscog.inventory.entity.Location;
import io.github.diegofranciscog.inventory.entity.Product;
import io.github.diegofranciscog.inventory.entity.ProductCost;
import io.github.diegofranciscog.inventory.entity.Role;
import io.github.diegofranciscog.inventory.entity.Stock;
import io.github.diegofranciscog.inventory.entity.Warehouse;
import io.github.diegofranciscog.inventory.exception.InsufficientStockException;
import io.github.diegofranciscog.inventory.repository.MovementLineRepository;
import io.github.diegofranciscog.inventory.repository.ProductCostRepository;
import io.github.diegofranciscog.inventory.repository.StockRepository;
import io.github.diegofranciscog.inventory.support.AbstractIntegrationTest;

/**
 * Criterio de aceptación: concurrencia segura con {@code @Version}. Se usan hilos reales contra PostgreSQL; cada hilo
 * es una transacción independiente, igual que dos usuarios operando a la vez.
 */
class ConcurrencyIT extends AbstractIntegrationTest {

    private static final int THREADS = 16;

    /** Detalle de cualquier excepción no prevista, para que el fallo del test diga qué pasó. */
    private final List<String> unexpected = new CopyOnWriteArrayList<>();

    @Autowired
    private MovementService movements;
    @Autowired
    private StockRepository stocks;
    @Autowired
    private ProductCostRepository costs;
    @Autowired
    private MovementLineRepository lines;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Warehouse quito;
    private Warehouse guayaquil;
    private Location quitoA;
    private Location guayaquilA;
    private AppUser supervisor;
    private Product soap;

    @BeforeEach
    void setUp() {
        quito = data.warehouse("BOD-UIO");
        guayaquil = data.warehouse("BOD-GYE");
        quitoA = data.location(quito, "01", "01", "1");
        guayaquilA = data.location(guayaquil, "01", "01", "1");
        supervisor = data.user("supervisor@test.local", Role.SUPERVISOR);
        soap = data.product("SOAP-1", false);
    }

    @Test
    void versionDetectsALostUpdateInsteadOfOverwritingIt() {
        movements.receive(receipt(quito, line(soap, quitoA, "10", "1")), supervisor.getId());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long stockId = stocks.findByProductIdAndLocationIdAndLotIsNull(soap.getId(), quitoA.getId()).orElseThrow().getId();

        // Usuario A lee el stock (versión N) y se queda con esa copia
        Stock staleCopy = tx.execute(status -> stocks.findById(stockId).orElseThrow());
        long versionRead = staleCopy.getVersion();
        // Usuario B modifica y confirma primero (versión N + 1)
        tx.executeWithoutResult(status -> stocks.findById(stockId).orElseThrow().decrease(BigDecimal.ONE, Instant.now()));

        // Usuario A intenta guardar su copia vieja: Hibernate compara la versión y rechaza la escritura
        staleCopy.decrease(new BigDecimal("5"), Instant.now());
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> stocks.save(staleCopy)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        Stock current = stocks.findById(stockId).orElseThrow();
        assertThat(current.getQuantity()).as("solo se aplicó la escritura de B").isEqualByComparingTo("9");
        assertThat(current.getVersion()).isEqualTo(versionRead + 1);
    }

    @Test
    void concurrentIssuesNeverOversellAndTheKardexStillBalances() throws Exception {
        movements.receive(receipt(quito, line(soap, quitoA, "10", "2.50")), supervisor.getId());

        List<Outcome> outcomes = runConcurrently(THREADS, () -> movements.issue(new IssueRequest(quito.getId(), "PED",
                "PED-X", null, List.of(new IssueLine(soap.getId(), BigDecimal.ONE, null, null, null))), supervisor.getId()));

        long succeeded = outcomes.stream().filter(outcome -> outcome == Outcome.OK).count();
        assertThat(outcomes).as("excepciones inesperadas: %s", unexpected).doesNotContain(Outcome.UNEXPECTED);
        assertThat(succeeded).as("nunca se venden más de 10").isLessThanOrEqualTo(10).isPositive();
        BigDecimal remaining = stocks.sumByProductAndWarehouse(soap.getId(), quito.getId());
        assertThat(remaining).isEqualByComparingTo(BigDecimal.valueOf(10 - succeeded)).isNotNegative();
        long issuedLines = lines.findAll().stream().filter(line -> line.getDirection() == Direction.OUT).count();
        assertThat(issuedLines).isEqualTo(succeeded);
        assertKardexBalanced();
    }

    @Test
    void concurrentReceiptsKeepTheWeightedAverageExact() throws Exception {
        List<Outcome> outcomes = runConcurrently(THREADS, () -> movements.receive(
                receipt(quito, line(soap, quitoA, "10", "3.00")), supervisor.getId()));

        long succeeded = outcomes.stream().filter(outcome -> outcome == Outcome.OK).count();
        assertThat(outcomes).as("excepciones inesperadas: %s", unexpected).doesNotContain(Outcome.UNEXPECTED, Outcome.INSUFFICIENT);
        ProductCost cost = costs.findById(soap.getId()).orElseThrow();
        assertThat(cost.getQuantityOnHand()).isEqualByComparingTo(BigDecimal.valueOf(10 * succeeded));
        assertThat(cost.getTotalValue()).isEqualByComparingTo(BigDecimal.valueOf(30 * succeeded));
        assertThat(cost.getAverageCost()).isEqualByComparingTo("3.00");
        assertKardexBalanced();
    }

    @Test
    void crossedTransfersBetweenTwoWarehousesDoNotCorruptStock() throws Exception {
        movements.receive(receipt(quito, line(soap, quitoA, "50", "1")), supervisor.getId());
        movements.receive(receipt(guayaquil, line(soap, guayaquilA, "50", "1")), supervisor.getId());

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            boolean fromQuito = i % 2 == 0;
            tasks.add(() -> movements.transfer(new TransferRequest(
                    fromQuito ? quito.getId() : guayaquil.getId(), fromQuito ? guayaquil.getId() : quito.getId(), "OT", "OT-X",
                    null, List.of(new TransferLine(soap.getId(), BigDecimal.ONE, null, null, null,
                    fromQuito ? guayaquilA.getId() : quitoA.getId()))), supervisor.getId()));
        }
        List<Outcome> outcomes = run(tasks);

        assertThat(outcomes).as("excepciones inesperadas: %s", unexpected).doesNotContain(Outcome.UNEXPECTED);
        BigDecimal total = stocks.sumByProductAndWarehouse(soap.getId(), quito.getId())
                .add(stocks.sumByProductAndWarehouse(soap.getId(), guayaquil.getId()));
        assertThat(total).as("las transferencias no crean ni destruyen stock").isEqualByComparingTo("100");
        assertKardexBalanced();
    }

    private List<Outcome> runConcurrently(int threads, Callable<Object> task) throws Exception {
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(task);
        }
        return run(tasks);
    }

    /** Arranca todas las tareas a la vez (compuerta) y clasifica el resultado de cada una. */
    private List<Outcome> run(List<Callable<Object>> tasks) throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(tasks.size())) {
            for (Callable<Object> task : tasks) {
                futures.add(executor.submit(() -> {
                    gate.await();
                    try {
                        task.call();
                        return Outcome.OK;
                    } catch (InsufficientStockException e) {
                        return Outcome.INSUFFICIENT;
                    } catch (ConcurrencyFailureException e) {
                        return Outcome.CONFLICT_AFTER_RETRIES;
                    } catch (Exception e) {
                        unexpected.add(e.getClass().getName() + ": " + e.getMessage());
                        return Outcome.UNEXPECTED;
                    }
                }));
            }
            gate.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        }
    }

    private enum Outcome { OK, INSUFFICIENT, CONFLICT_AFTER_RETRIES, UNEXPECTED }
}
