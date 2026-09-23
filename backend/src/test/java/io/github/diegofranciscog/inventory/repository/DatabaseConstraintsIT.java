package io.github.diegofranciscog.inventory.repository;

import static io.github.diegofranciscog.inventory.support.TestData.line;
import static io.github.diegofranciscog.inventory.support.TestData.receipt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.diegofranciscog.inventory.entity.AppUser;
import io.github.diegofranciscog.inventory.entity.Location;
import io.github.diegofranciscog.inventory.entity.Product;
import io.github.diegofranciscog.inventory.entity.Role;
import io.github.diegofranciscog.inventory.entity.Warehouse;
import io.github.diegofranciscog.inventory.service.MovementService;
import io.github.diegofranciscog.inventory.support.AbstractIntegrationTest;

/** Las reglas críticas también las defiende la base de datos (última barrera si falla el código). */
class DatabaseConstraintsIT extends AbstractIntegrationTest {

    @Autowired
    private MovementService movements;

    private Product soap;
    private Location location;
    private AppUser supervisor;

    @BeforeEach
    void setUp() {
        Warehouse quito = data.warehouse("BOD-UIO");
        location = data.location(quito, "01", "01", "1");
        supervisor = data.user("supervisor@test.local", Role.SUPERVISOR);
        soap = data.product("SOAP-1", false);
        movements.receive(receipt(quito, line(soap, location, "5", "1.00")), supervisor.getId());
    }

    @Test
    void flywayAppliedEveryMigration() {
        Integer applied = jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class);
        assertThat(applied).isEqualTo(4);
    }

    @Test
    void stockCannotBeNegativeEvenWithDirectSql() {
        assertThatThrownBy(() -> jdbc.update("update stock set quantity = -1 where product_id = ?", soap.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_non_negative");
    }

    @Test
    void kardexAndAuditLogAreAppendOnly() {
        assertThatThrownBy(() -> jdbc.update("update movement_line set quantity = 999"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("solo inserción");
        assertThatThrownBy(() -> jdbc.update("delete from inventory_movement"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("solo inserción");
        assertThatThrownBy(() -> jdbc.update("delete from audit_log"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("solo inserción");
    }

    @Test
    void stockWithoutLotIsUniquePerProductAndLocation() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into stock (product_id, warehouse_id, location_id, lot_id, quantity) "
                        + "select product_id, warehouse_id, location_id, null, 1 from stock where product_id = ?",
                soap.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theSameUserCannotSubmitAndReviewACount() {
        Long warehouseId = jdbc.queryForObject("select id from warehouse", Long.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into cycle_count (number, warehouse_id, status, created_by_id, submitted_by_id, reviewed_by_id,
                                         reviewed_at)
                values ('CC-TEST', ?, 'APPROVED', ?, ?, ?, now())
                """, warehouseId, supervisor.getId(), supervisor.getId(), supervisor.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_cycle_count_segregation");
    }

    @Test
    void stockMustLiveInALocationOfItsOwnWarehouse() {
        Warehouse other = data.warehouse("BOD-GYE");
        Product gloves = data.product("GLOVES-1", false);
        assertThatThrownBy(() -> jdbc.update(
                "insert into stock (product_id, warehouse_id, location_id, quantity) values (?, ?, ?, 1)",
                gloves.getId(), other.getId(), location.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_stock_location");
    }
}
