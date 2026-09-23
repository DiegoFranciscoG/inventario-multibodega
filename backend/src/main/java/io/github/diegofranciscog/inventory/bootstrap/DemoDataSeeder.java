package io.github.diegofranciscog.inventory.bootstrap;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import io.github.diegofranciscog.inventory.config.AppProperties;
import io.github.diegofranciscog.inventory.dto.catalog.CatalogResponses.CategoryResponse;
import io.github.diegofranciscog.inventory.dto.catalog.CategoryRequest;
import io.github.diegofranciscog.inventory.dto.catalog.ProductRequest;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.CountLineRequest;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.CreateRequest;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.CycleCountResponse;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.LineResponse;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.AdjustmentLine;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.AdjustmentRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.IssueLine;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.IssueRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.ReceiptLine;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.ReceiptRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.TransferLine;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.TransferRequest;
import io.github.diegofranciscog.inventory.dto.storage.StorageRequests.LocationRequest;
import io.github.diegofranciscog.inventory.dto.storage.StorageRequests.MinStockRequest;
import io.github.diegofranciscog.inventory.dto.storage.StorageRequests.WarehouseRequest;
import io.github.diegofranciscog.inventory.dto.storage.StorageResponses.LocationResponse;
import io.github.diegofranciscog.inventory.dto.storage.StorageResponses.WarehouseResponse;
import io.github.diegofranciscog.inventory.entity.AbcClass;
import io.github.diegofranciscog.inventory.entity.AppUser;
import io.github.diegofranciscog.inventory.entity.LocationType;
import io.github.diegofranciscog.inventory.entity.Role;
import io.github.diegofranciscog.inventory.gs1.Gtin;
import io.github.diegofranciscog.inventory.repository.AppUserRepository;
import io.github.diegofranciscog.inventory.repository.ProductRepository;
import io.github.diegofranciscog.inventory.repository.WarehouseRepository;
import io.github.diegofranciscog.inventory.service.CatalogService;
import io.github.diegofranciscog.inventory.service.CycleCountService;
import io.github.diegofranciscog.inventory.service.MovementPostingService;
import io.github.diegofranciscog.inventory.service.ProductService;
import io.github.diegofranciscog.inventory.service.WarehouseService;

/**
 * Datos de demostración FICTICIOS (APP_DEMO_ENABLED=true): bodegas en Quito, Guayaquil y Cuenca, productos con GTIN de
 * prefijo GS1 952 (reservado para demos, R-15) y 60 días de movimientos. Todo se registra con los servicios de negocio,
 * así el kárdex, el stock y el costo promedio de la demo cuadran por construcción (R-09).
 */
@Component
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {

    /** Prefijo de empresa ficticio dentro del rango GS1 952 (demostraciones). */
    private static final String DEMO_COMPANY_PREFIX = "9527001";
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AppProperties properties;
    private final ProductRepository products;
    private final AppUserRepository users;
    private final WarehouseRepository warehouseRepository;
    private final PasswordEncoder passwordEncoder;
    private final WarehouseService warehouses;
    private final CatalogService catalog;
    private final ProductService productService;
    private final MovementPostingService posting;
    private final CycleCountService cycleCounts;
    private final Clock clock;

    private final Map<String, Long> productIds = new HashMap<>();
    private final Map<String, Long> locationIds = new HashMap<>();
    private final Map<String, Long> warehouseIds = new HashMap<>();

    public DemoDataSeeder(AppProperties properties, ProductRepository products, AppUserRepository users,
                          WarehouseRepository warehouseRepository, PasswordEncoder passwordEncoder,
                          WarehouseService warehouses, CatalogService catalog, ProductService productService,
                          MovementPostingService posting, CycleCountService cycleCounts, Clock clock) {
        this.properties = properties;
        this.products = products;
        this.users = users;
        this.warehouseRepository = warehouseRepository;
        this.passwordEncoder = passwordEncoder;
        this.warehouses = warehouses;
        this.catalog = catalog;
        this.productService = productService;
        this.posting = posting;
        this.cycleCounts = cycleCounts;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.demo().enabled() || products.count() > 0) {
            return;
        }
        String password = properties.demo().password();
        if (password == null || password.length() < AdminBootstrap.MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("Con APP_DEMO_ENABLED=true, DEMO_PASSWORD debe tener al menos "
                    + AdminBootstrap.MIN_PASSWORD_LENGTH + " caracteres");
        }
        seedWarehouses();
        seedCatalog();
        Map<String, Long> userIds = seedUsers(password);
        seedMovements(userIds.get("supervisor"));
        seedMinimums();
        seedPendingCycleCount(userIds.get("operador"));
        log.info("Datos de demostración creados");
    }

    private void seedWarehouses() {
        warehouse("BOD-UIO", "Bodega Quito Norte", "Quito", "Av. Demo N12-34 y Calle Ficticia");
        warehouse("BOD-GYE", "Bodega Guayaquil Sur", "Guayaquil", "Km 5 vía Ejemplo, galpón 3");
        warehouse("BOD-CUE", "Bodega Cuenca", "Cuenca", "Parque industrial Demo, lote 8");
    }

    private void warehouse(String code, String name, String city, String address) {
        WarehouseResponse warehouse = warehouses.create(new WarehouseRequest(code, name, city, address, true));
        warehouseIds.put(code, warehouse.id());
        location(warehouse, "00", "00", "0", LocationType.RECEIVING);
        for (String aisle : List.of("01", "02")) {
            for (String rack : List.of("01", "02")) {
                for (String level : List.of("1", "2")) {
                    location(warehouse, aisle, rack, level, LocationType.STORAGE);
                }
            }
        }
        location(warehouse, "99", "01", "1", LocationType.QUARANTINE);
    }

    private void location(WarehouseResponse warehouse, String aisle, String rack, String level, LocationType type) {
        LocationResponse location = warehouses.createLocation(warehouse.id(), new LocationRequest(aisle, rack, level, type, true));
        locationIds.put(warehouse.code() + "/" + location.code(), location.id());
    }

    private void seedCatalog() {
        Map<String, Long> categories = new HashMap<>();
        for (String[] category : new String[][]{
                {"ALIM", "Alimentos"}, {"BEB", "Bebidas"}, {"LIMP", "Limpieza"}, {"BOTIQ", "Botiquín"}, {"FERR", "Ferretería"}}) {
            CategoryResponse created = catalog.createCategory(new CategoryRequest(category[0], category[1], true));
            categories.put(category[0], created.id());
        }
        product("ALI-ARROZ-2KG", 1, "Arroz blanco funda 2 kg", categories.get("ALIM"), "H87", true, AbcClass.A, null);
        product("ALI-ACEITE-1L", 2, "Aceite vegetal botella 1 L", categories.get("ALIM"), "H87", true, AbcClass.A,
                caseOf(2, 12));
        product("BEB-AGUA-600", 3, "Agua sin gas 600 ml", categories.get("BEB"), "H87", true, AbcClass.B, caseOf(3, 24));
        product("BEB-JUGO-1L", 4, "Jugo de naranja 1 L", categories.get("BEB"), "H87", true, AbcClass.B, null);
        product("LIM-DETER-1KG", 5, "Detergente en polvo 1 kg", categories.get("LIMP"), "H87", false, AbcClass.B, null);
        product("LIM-CLORO-1L", 6, "Cloro desinfectante 1 L", categories.get("LIMP"), "H87", false, AbcClass.C, null);
        product("BOT-ALCOHOL-500", 7, "Alcohol antiséptico 70 % 500 ml", categories.get("BOTIQ"), "H87", true, AbcClass.A, null);
        product("BOT-GUANTES-100", 8, "Guantes de nitrilo caja x100", categories.get("BOTIQ"), "H87", false, AbcClass.C, null);
        product("FER-TORN-6MM", 9, "Tornillo autorroscante 6 mm", categories.get("FERR"), "H87", false, AbcClass.C,
                List.of(new ProductRequest.Conversion("DZN", new BigDecimal("12"), null)));
        product("FER-CINTA-48", 10, "Cinta de embalaje 48 mm x 50 m", categories.get("FERR"), "H87", false, AbcClass.C, null);
    }

    private void product(String sku, int itemReference, String name, Long categoryId, String uom, boolean lotControlled,
                         AbcClass abcClass, List<ProductRequest.Conversion> conversions) {
        Long id = productService.create(new ProductRequest(sku, gtin13(itemReference), name, null, categoryId, uom,
                lotControlled, abcClass, true, conversions)).id();
        productIds.put(sku, id);
    }

    /** Caja con GTIN-14: indicador 1 + primeros 12 dígitos del GTIN-13 + nuevo dígito verificador. */
    private static List<ProductRequest.Conversion> caseOf(int itemReference, int unitsPerCase) {
        String body = "1" + gtin13(itemReference).substring(0, 12);
        return List.of(new ProductRequest.Conversion("XBX", new BigDecimal(unitsPerCase), body + Gtin.checkDigit(body)));
    }

    static String gtin13(int itemReference) {
        String body = DEMO_COMPANY_PREFIX + "%05d".formatted(itemReference);
        return body + Gtin.checkDigit(body);
    }

    private Map<String, Long> seedUsers(String password) {
        String hash = passwordEncoder.encode(password);
        AppUser supervisor = users.save(new AppUser("supervisor@demo.local", "Supervisora Demo", hash, Role.SUPERVISOR));
        AppUser operator = new AppUser("operador@demo.local", "Operador Demo", hash, Role.OPERATOR);
        operator.assignWarehouses(Set.of(warehouseRepository.findByCode("BOD-UIO").orElseThrow(),
                warehouseRepository.findByCode("BOD-GYE").orElseThrow()));
        users.save(operator);
        users.save(new AppUser("auditor@demo.local", "Auditor Demo", hash, Role.AUDITOR));
        return Map.of("supervisor", supervisor.getId(), "operador", operator.getId());
    }

    private void seedMovements(Long userId) {
        Instant start = Instant.now(clock).minus(Duration.ofDays(60));
        LocalDate today = LocalDate.now(clock.withZone(properties.inventory().timeZone()));

        posting.receive(new ReceiptRequest(warehouseIds.get("BOD-UIO"), "SI", "SI-2026-001", "Saldo inicial", List.of(
                receipt("ALI-ARROZ-2KG", "BOD-UIO", "P01-E01-N1", "200", null, "1.85", "L-ARZ-01", today.plusDays(300)),
                receipt("ALI-ACEITE-1L", "BOD-UIO", "P01-E01-N2", "10", "XBX", "28.80", "L-ACE-01", today.plusDays(400)),
                receipt("BEB-AGUA-600", "BOD-UIO", "P01-E02-N1", "240", null, "0.35", "L-AGU-02", today.plusDays(200)),
                receipt("LIM-DETER-1KG", "BOD-UIO", "P02-E01-N1", "60", null, "3.10", null, null),
                receipt("LIM-CLORO-1L", "BOD-UIO", "P02-E01-N2", "80", null, "0.95", null, null),
                receipt("BOT-ALCOHOL-500", "BOD-UIO", "P02-E02-N1", "50", null, "2.75", "L-ALC-01", today.plusDays(600)),
                receipt("BOT-GUANTES-100", "BOD-UIO", "P02-E02-N2", "30", null, "6.50", null, null),
                receipt("FER-TORN-6MM", "BOD-UIO", "P01-E02-N2", "500", null, "0.04", null, null),
                receipt("FER-CINTA-48", "BOD-UIO", "P01-E02-N2", "40", null, "1.20", null, null))), userId, start);

        posting.receive(new ReceiptRequest(warehouseIds.get("BOD-GYE"), "01", "001-001-000000123", "Compra a proveedor demo",
                List.of(
                        receipt("ALI-ARROZ-2KG", "BOD-GYE", "P01-E01-N1", "100", null, "1.95", "L-ARZ-02", today.plusDays(20)),
                        receipt("BEB-AGUA-600", "BOD-GYE", "P01-E02-N1", "120", null, "0.38", "L-AGU-01", today.plusDays(15)),
                        receipt("BEB-JUGO-1L", "BOD-GYE", "P01-E01-N2", "60", null, "1.10", "L-JUG-01", today.minusDays(3)),
                        receipt("LIM-DETER-1KG", "BOD-GYE", "P02-E01-N1", "40", null, "3.25", null, null))),
                userId, start.plus(Duration.ofDays(15)));

        posting.receive(new ReceiptRequest(warehouseIds.get("BOD-UIO"), "01", "001-001-000000456", "Reposición", List.of(
                receipt("ALI-ARROZ-2KG", "BOD-UIO", "P01-E01-N1", "150", null, "2.05", "L-ARZ-03", today.plusDays(340)),
                receipt("ALI-ACEITE-1L", "BOD-UIO", "P01-E01-N2", "5", "XBX", "30.00", "L-ACE-02", today.plusDays(380)))),
                userId, start.plus(Duration.ofDays(30)));

        posting.issue(new IssueRequest(warehouseIds.get("BOD-UIO"), "PED", "PED-0101", "Despacho a cliente demo", List.of(
                new IssueLine(productIds.get("ALI-ARROZ-2KG"), new BigDecimal("120"), null, null, null),
                new IssueLine(productIds.get("ALI-ACEITE-1L"), new BigDecimal("30"), null, null, null),
                new IssueLine(productIds.get("LIM-DETER-1KG"), new BigDecimal("25"), null, null, null),
                new IssueLine(productIds.get("FER-TORN-6MM"), new BigDecimal("10"), "DZN", null, null))),
                userId, start.plus(Duration.ofDays(40)));

        posting.transfer(new TransferRequest(warehouseIds.get("BOD-UIO"), warehouseIds.get("BOD-CUE"), "OT", "OT-0007",
                "Abastecimiento sucursal Cuenca", List.of(
                transfer("ALI-ARROZ-2KG", "50", "BOD-CUE", "P01-E01-N1"),
                transfer("BEB-AGUA-600", "60", "BOD-CUE", "P01-E02-N1"),
                transfer("BOT-ALCOHOL-500", "10", "BOD-CUE", "P02-E01-N1"))),
                userId, start.plus(Duration.ofDays(45)));

        posting.issue(new IssueRequest(warehouseIds.get("BOD-GYE"), "PED", "PED-0102", "Despacho a cliente demo", List.of(
                new IssueLine(productIds.get("BEB-AGUA-600"), new BigDecimal("40"), null, null, null),
                new IssueLine(productIds.get("LIM-DETER-1KG"), new BigDecimal("30"), null, null, null))),
                userId, start.plus(Duration.ofDays(50)));

        posting.adjust(new AdjustmentRequest(warehouseIds.get("BOD-UIO"), "AJ", "AJ-0003", "Merma por empaque dañado",
                List.of(new AdjustmentLine(productIds.get("LIM-CLORO-1L"), locationIds.get("BOD-UIO/P02-E01-N2"), null, null,
                        null, new BigDecimal("-3"), null))),
                userId, start.plus(Duration.ofDays(55)), null);

        posting.issue(new IssueRequest(warehouseIds.get("BOD-CUE"), "06", "001-001-000000789", "Guía de remisión demo",
                List.of(new IssueLine(productIds.get("ALI-ARROZ-2KG"), new BigDecimal("20"), null, null, null))),
                userId, start.plus(Duration.ofDays(58)));
    }

    private void seedMinimums() {
        minimum("ALI-ARROZ-2KG", "BOD-CUE", "50");
        minimum("LIM-DETER-1KG", "BOD-GYE", "20");
        minimum("BEB-AGUA-600", "BOD-UIO", "100");
        minimum("BOT-GUANTES-100", "BOD-UIO", "40");
    }

    private void minimum(String sku, String warehouseCode, String quantity) {
        productService.setMinimum(productIds.get(sku), new MinStockRequest(warehouseIds.get(warehouseCode), new BigDecimal(quantity)));
    }

    /** Conteo del pasillo 02 de Quito, contado por el operador y enviado: queda pendiente de aprobación. */
    private void seedPendingCycleCount(Long operatorId) {
        CycleCountResponse count = cycleCounts.create(
                new CreateRequest(warehouseIds.get("BOD-UIO"), "02", null, "Conteo cíclico semanal del pasillo 02"), operatorId);
        Map<String, String> counted = Map.of(
                "LIM-DETER-1KG", "35",
                "LIM-CLORO-1L", "75",
                "BOT-ALCOHOL-500", "40",
                "BOT-GUANTES-100", "31");
        for (LineResponse line : count.lines()) {
            String quantity = counted.getOrDefault(line.sku(), "0");
            cycleCounts.registerCount(count.id(), line.id(), new CountLineRequest(new BigDecimal(quantity), null), operatorId);
        }
        cycleCounts.submit(count.id(), operatorId);
    }

    private ReceiptLine receipt(String sku, String warehouseCode, String locationCode, String quantity, String uom,
                                String unitCost, String lot, LocalDate expiry) {
        return new ReceiptLine(productIds.get(sku), locationIds.get(warehouseCode + "/" + locationCode),
                new BigDecimal(quantity), uom, new BigDecimal(unitCost), lot, expiry, null);
    }

    private TransferLine transfer(String sku, String quantity, String targetWarehouse, String targetLocation) {
        return new TransferLine(productIds.get(sku), new BigDecimal(quantity), null, null, null,
                locationIds.get(targetWarehouse + "/" + targetLocation));
    }
}
