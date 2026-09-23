package io.github.diegofranciscog.inventory.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.github.diegofranciscog.inventory.dto.report.ReportResponses.ReconciliationResponse;
import io.github.diegofranciscog.inventory.security.LoginRateLimiter;
import io.github.diegofranciscog.inventory.service.ReportService;

/**
 * Base de los tests de integración: PostgreSQL 17 real en Testcontainers (misma versión que producción), migraciones
 * Flyway y contexto Spring completo. El secreto JWT se genera al azar en cada ejecución: no hay secretos en el repo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestData.class)
public abstract class AbstractIntegrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11-alpine3.24");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected TestData data;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ReportService reports;

    @Autowired
    private LoginRateLimiter rateLimiter;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USER", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        registry.add("DB_POOL_SIZE", () -> "12");
        registry.add("JWT_SECRET", AbstractIntegrationTest::randomSecret);
        registry.add("CORS_ALLOWED_ORIGINS", () -> "http://localhost:4200");
    }

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("""
                truncate table audit_log, cycle_count_line, movement_line, inventory_movement, cycle_count,
                    stock_min_rule, stock, lot, product_cost, product_uom_conversion, product, category,
                    location, user_warehouse, warehouse, app_user
                restart identity cascade
                """);
        rateLimiter.reset();
    }

    /** R-09: después de cada escenario el kárdex debe cuadrar con el stock y la valorización. */
    protected void assertKardexBalanced() {
        ReconciliationResponse reconciliation = reports.reconciliation();
        assertThat(reconciliation.rows()).as("conciliación por producto").allMatch(row -> row.balanced());
        assertThat(reconciliation.balanced()).isTrue();
    }

    private static String randomSecret() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
