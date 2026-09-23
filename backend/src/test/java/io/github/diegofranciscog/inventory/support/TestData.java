package io.github.diegofranciscog.inventory.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.diegofranciscog.inventory.dto.catalog.ProductRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.ReceiptLine;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.ReceiptRequest;
import io.github.diegofranciscog.inventory.entity.AbcClass;
import io.github.diegofranciscog.inventory.entity.AppUser;
import io.github.diegofranciscog.inventory.entity.Category;
import io.github.diegofranciscog.inventory.entity.Location;
import io.github.diegofranciscog.inventory.entity.LocationType;
import io.github.diegofranciscog.inventory.entity.Product;
import io.github.diegofranciscog.inventory.entity.Role;
import io.github.diegofranciscog.inventory.entity.Warehouse;
import io.github.diegofranciscog.inventory.repository.AppUserRepository;
import io.github.diegofranciscog.inventory.repository.CategoryRepository;
import io.github.diegofranciscog.inventory.repository.LocationRepository;
import io.github.diegofranciscog.inventory.repository.ProductRepository;
import io.github.diegofranciscog.inventory.repository.WarehouseRepository;
import io.github.diegofranciscog.inventory.service.ProductService;

/** Crea datos de prueba mínimos. La contraseña se genera en cada ejecución (no hay credenciales en el repositorio). */
@TestComponent
public class TestData {

    public static final String PASSWORD = "Pw-" + UUID.randomUUID();

    private final AtomicInteger sequence = new AtomicInteger();
    private final AppUserRepository users;
    private final WarehouseRepository warehouses;
    private final LocationRepository locations;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductService productService;
    private final PasswordEncoder passwordEncoder;

    public TestData(AppUserRepository users, WarehouseRepository warehouses, LocationRepository locations,
                    CategoryRepository categories, ProductRepository products, ProductService productService,
                    PasswordEncoder passwordEncoder) {
        this.users = users;
        this.warehouses = warehouses;
        this.locations = locations;
        this.categories = categories;
        this.products = products;
        this.productService = productService;
        this.passwordEncoder = passwordEncoder;
    }

    public AppUser user(String email, Role role, Warehouse... assigned) {
        AppUser user = new AppUser(email, "Usuario de prueba", passwordEncoder.encode(PASSWORD), role);
        user.assignWarehouses(Set.of(assigned));
        return users.save(user);
    }

    public Warehouse warehouse(String code) {
        return warehouses.save(new Warehouse(code, "Bodega " + code, "Quito", null));
    }

    public Location location(Warehouse warehouse, String aisle, String rack, String level) {
        return locations.save(new Location(warehouse, aisle, rack, level, LocationType.STORAGE));
    }

    public Product product(String sku, boolean lotControlled, ProductRequest.Conversion... conversions) {
        return product(sku, null, lotControlled, conversions);
    }

    public Product product(String sku, String gtin, boolean lotControlled, ProductRequest.Conversion... conversions) {
        Category category = categories.save(new Category("CAT" + sequence.incrementAndGet(), "Categoría de prueba"));
        Long id = productService.create(new ProductRequest(sku, gtin, "Producto " + sku, null, category.getId(), "H87",
                lotControlled, AbcClass.A, true, List.of(conversions))).id();
        return products.findWithDetailsById(id).orElseThrow();
    }

    public static ReceiptRequest receipt(Warehouse warehouse, ReceiptLine... lines) {
        return new ReceiptRequest(warehouse.getId(), "01", "001-001-" + UUID.randomUUID().toString().substring(0, 9),
                null, List.of(lines));
    }

    public static ReceiptLine line(Product product, Location location, String quantity, String unitCost) {
        return new ReceiptLine(product.getId(), location.getId(), new BigDecimal(quantity), null, new BigDecimal(unitCost),
                null, null, null);
    }

    public static ReceiptLine lotLine(Product product, Location location, String quantity, String unitCost, String lot,
                                      LocalDate expiry) {
        return new ReceiptLine(product.getId(), location.getId(), new BigDecimal(quantity), null, new BigDecimal(unitCost),
                lot, expiry, null);
    }
}
