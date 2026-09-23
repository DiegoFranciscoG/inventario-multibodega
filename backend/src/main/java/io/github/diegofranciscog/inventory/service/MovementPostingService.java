package io.github.diegofranciscog.inventory.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.diegofranciscog.inventory.config.AppProperties;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.AdjustmentLine;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.AdjustmentRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.IssueLine;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.IssueRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.ReceiptLine;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.ReceiptRequest;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.TransferLine;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.TransferRequest;
import io.github.diegofranciscog.inventory.entity.AppUser;
import io.github.diegofranciscog.inventory.entity.CostPosting;
import io.github.diegofranciscog.inventory.entity.Direction;
import io.github.diegofranciscog.inventory.entity.InventoryMovement;
import io.github.diegofranciscog.inventory.entity.Location;
import io.github.diegofranciscog.inventory.entity.Lot;
import io.github.diegofranciscog.inventory.entity.MovementLine;
import io.github.diegofranciscog.inventory.entity.MovementType;
import io.github.diegofranciscog.inventory.entity.Product;
import io.github.diegofranciscog.inventory.entity.ProductCost;
import io.github.diegofranciscog.inventory.entity.Stock;
import io.github.diegofranciscog.inventory.entity.Warehouse;
import io.github.diegofranciscog.inventory.exception.BusinessRuleException;
import io.github.diegofranciscog.inventory.exception.InsufficientStockException;
import io.github.diegofranciscog.inventory.exception.NotFoundException;
import io.github.diegofranciscog.inventory.repository.InventoryMovementRepository;
import io.github.diegofranciscog.inventory.repository.LotRepository;
import io.github.diegofranciscog.inventory.repository.MovementLineRepository;
import io.github.diegofranciscog.inventory.repository.ProductCostRepository;
import io.github.diegofranciscog.inventory.repository.StockRepository;

/**
 * Contabiliza movimientos de inventario dentro de UNA transacción: stock por ubicación y lote, costo promedio y
 * renglones del kárdex (R-01 a R-10, R-18, R-24). Si cualquier renglón falla, no se aplica nada (R-08).
 * <p>
 * Los renglones se procesan ordenados por producto para que dos transacciones concurrentes bloqueen filas en el mismo
 * orden y reducir interbloqueos. Los conflictos de concurrencia ({@code @Version}) los reintenta {@link MovementService}.
 * <p>
 * {@code requestedAt} nulo = hora del servidor (caso de la API). Una fecha explícita (carga de datos) no puede ser
 * anterior al último movimiento del producto (R-24); el orden del kárdex es siempre el orden de registro.
 */
@Service
public class MovementPostingService {

    private final ReferenceResolver references;
    private final WarehouseAccessService access;
    private final StockRepository stocks;
    private final LotRepository lots;
    private final ProductCostRepository costs;
    private final InventoryMovementRepository movements;
    private final MovementLineRepository lines;
    private final AuditService audit;
    private final AppProperties properties;
    private final Clock clock;

    public MovementPostingService(ReferenceResolver references, WarehouseAccessService access, StockRepository stocks,
                                  LotRepository lots, ProductCostRepository costs, InventoryMovementRepository movements,
                                  MovementLineRepository lines, AuditService audit, AppProperties properties,
                                  Clock clock) {
        this.references = references;
        this.access = access;
        this.stocks = stocks;
        this.lots = lots;
        this.costs = costs;
        this.movements = movements;
        this.lines = lines;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ ingreso

    @Transactional
    public InventoryMovement receive(ReceiptRequest request, Long userId, Instant requestedAt) {
        Timing timing = timing(requestedAt);
        Instant at = timing.at();
        AppUser user = references.activeUser(userId);
        Warehouse warehouse = references.activeWarehouse(request.warehouseId());
        access.requireCanOperate(user, warehouse);
        InventoryMovement movement = open(MovementType.RECEIPT, warehouse, null, request.referenceTypeCode(),
                request.referenceNumber(), request.reason(), at, user, null);
        for (ReceiptLine line : sortedByProduct(request.lines(), ReceiptLine::productId)) {
            Product product = references.activeProduct(line.productId());
            Location location = references.activeLocation(line.locationId(), warehouse);
            Quantity quantity = toBase(product, line.uomCode(), line.quantity());
            BigDecimal unitCost = line.unitCost().divide(quantity.factor(), ProductCost.COST_SCALE, ProductCost.ROUNDING);
            Lot lot = resolveReceiptLot(product, line.lotNumber(), line.expiryDate(), line.productionDate());
            Stock stock = lockStock(product, warehouse, location, lot);
            postIn(movement, stock, quantity, cost -> cost.receive(quantity.base(), unitCost, at), timing);
        }
        return finish(movement);
    }

    // ------------------------------------------------------------------ egreso

    @Transactional
    public InventoryMovement issue(IssueRequest request, Long userId, Instant requestedAt) {
        Timing timing = timing(requestedAt);
        Instant at = timing.at();
        AppUser user = references.activeUser(userId);
        Warehouse warehouse = references.activeWarehouse(request.warehouseId());
        access.requireCanOperate(user, warehouse);
        InventoryMovement movement = open(MovementType.ISSUE, warehouse, null, request.referenceTypeCode(),
                request.referenceNumber(), request.reason(), at, user, null);
        LocalDate today = today(at);
        for (IssueLine line : sortedByProduct(request.lines(), IssueLine::productId)) {
            Product product = references.activeProduct(line.productId());
            productCost(product, timing);
            Quantity quantity = toBase(product, line.uomCode(), line.quantity());
            for (Allocation allocation : allocate(product, warehouse, line.locationId(), line.lotId(), quantity.base(),
                    false, today)) {
                postOut(movement, allocation.stock(), quantity.portion(allocation.quantity()), timing);
            }
        }
        return finish(movement);
    }

    // ------------------------------------------------------------------ transferencia

    /** Salida del origen y entrada en el destino en la misma transacción (R-08); el costo no cambia (R-03). */
    @Transactional
    public InventoryMovement transfer(TransferRequest request, Long userId, Instant requestedAt) {
        Timing timing = timing(requestedAt);
        Instant at = timing.at();
        AppUser user = references.activeUser(userId);
        Warehouse source = references.activeWarehouse(request.sourceWarehouseId());
        Warehouse target = references.activeWarehouse(request.targetWarehouseId());
        access.requireCanOperate(user, source);
        access.requireCanOperate(user, target);
        InventoryMovement movement = open(MovementType.TRANSFER, source, target, request.referenceTypeCode(),
                request.referenceNumber(), request.reason(), at, user, null);
        LocalDate today = today(at);
        for (TransferLine line : sortedByProduct(request.lines(), TransferLine::productId)) {
            Product product = references.activeProduct(line.productId());
            productCost(product, timing);
            Quantity quantity = toBase(product, line.uomCode(), line.quantity());
            Location destination = references.activeLocation(line.toLocationId(), target);
            // Un lote vencido solo se mueve si se indica explícitamente (p. ej. hacia cuarentena).
            boolean explicitLot = line.lotId() != null;
            for (Allocation allocation : allocate(product, source, line.fromLocationId(), line.lotId(), quantity.base(),
                    explicitLot, today)) {
                if (Objects.equals(allocation.stock().getLocation().getId(), destination.getId())) {
                    throw new BusinessRuleException("SAME_LOCATION", "El origen y el destino son la misma ubicación");
                }
                Quantity part = quantity.portion(allocation.quantity());
                CostPosting out = postOut(movement, allocation.stock(), part, timing);
                Stock targetStock = lockStock(product, target, destination, allocation.stock().getLot());
                postIn(movement, targetStock, part,
                        cost -> cost.transferIn(part.base(), out.unitCost(), out.totalCost(), at), timing);
            }
        }
        return finish(movement);
    }

    // ------------------------------------------------------------------ ajuste

    /**
     * Ajuste positivo o negativo. Los positivos entran al costo promedio vigente; si el producto nunca tuvo costo se
     * usa el costo indicado. {@code cycleCountId} enlaza el ajuste con el conteo que lo originó (R-21).
     */
    @Transactional
    public InventoryMovement adjust(AdjustmentRequest request, Long userId, Instant requestedAt, Long cycleCountId) {
        Timing timing = timing(requestedAt);
        Instant at = timing.at();
        AppUser user = references.activeUser(userId);
        Warehouse warehouse = references.activeWarehouse(request.warehouseId());
        access.requireCanOperate(user, warehouse);
        InventoryMovement movement = open(MovementType.ADJUSTMENT, warehouse, null, request.referenceTypeCode(),
                request.referenceNumber(), request.reason(), at, user, cycleCountId);
        for (AdjustmentLine line : sortedByProduct(request.lines(), AdjustmentLine::productId)) {
            BigDecimal delta = line.quantityDelta();
            if (delta.signum() == 0) {
                throw new BusinessRuleException("ZERO_ADJUSTMENT", "La cantidad del ajuste no puede ser cero");
            }
            Product product = references.activeProduct(line.productId());
            productCost(product, timing);
            Location location = references.activeLocation(line.locationId(), warehouse);
            boolean positive = delta.signum() > 0;
            Lot lot = resolveAdjustmentLot(product, line, positive);
            Quantity quantity = toBase(product, null, delta.abs());
            if (positive) {
                Stock stock = lockStock(product, warehouse, location, lot);
                postIn(movement, stock, quantity,
                        cost -> cost.receive(quantity.base(), positiveAdjustmentCost(cost, line.unitCost()), at), timing);
            } else {
                Stock stock = existingStock(product, location, lot)
                        .orElseThrow(() -> new InsufficientStockException(product.getSku(), location.getCode(),
                                BigDecimal.ZERO, quantity.base()));
                postOut(movement, stock, quantity, timing);
            }
        }
        return finish(movement);
    }

    // ------------------------------------------------------------------ piezas comunes

    private InventoryMovement open(MovementType type, Warehouse warehouse, Warehouse target, String referenceTypeCode,
                                   String referenceNumber, String reason, Instant at, AppUser user, Long cycleCountId) {
        String number = type.formatNumber(movements.nextNumber());
        return movements.save(new InventoryMovement(number, type, warehouse, target,
                references.documentType(referenceTypeCode), referenceNumber.strip(), blankToNull(reason), at, user,
                cycleCountId));
    }

    private InventoryMovement finish(InventoryMovement movement) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("number", movement.getNumber());
        details.put("type", movement.getType().name());
        details.put("warehouse", movement.getWarehouse().getCode());
        if (movement.getTargetWarehouse() != null) {
            details.put("targetWarehouse", movement.getTargetWarehouse().getCode());
        }
        details.put("reference", movement.getReferenceType().getCode() + " " + movement.getReferenceNumber());
        details.put("lines", movement.getLines().size());
        audit.record("MOVEMENT_POSTED", "InventoryMovement", movement.getId(), details);
        return movement;
    }

    private void postIn(InventoryMovement movement, Stock stock, Quantity quantity,
                        Function<ProductCost, CostPosting> costing, Timing timing) {
        ProductCost cost = productCost(stock.getProduct(), timing);
        stock.increase(quantity.base(), timing.at());
        CostPosting posting = costing.apply(cost);
        writeLine(movement, stock, Direction.IN, quantity, posting);
    }

    private CostPosting postOut(InventoryMovement movement, Stock stock, Quantity quantity, Timing timing) {
        ProductCost cost = productCost(stock.getProduct(), timing);
        stock.decrease(quantity.base(), timing.at());
        CostPosting posting = cost.issue(quantity.base(), timing.at());
        writeLine(movement, stock, Direction.OUT, quantity, posting);
        return posting;
    }

    private void writeLine(InventoryMovement movement, Stock stock, Direction direction, Quantity quantity,
                           CostPosting posting) {
        // La consulta de suma provoca el flush de este stock: el saldo de bodega incluye el renglón actual.
        BigDecimal warehouseBalance = stocks.sumByProductAndWarehouse(stock.getProduct().getId(), stock.getWarehouse().getId());
        lines.save(new MovementLine(movement, stock, direction, quantity.base(), quantity.uomCode(),
                quantity.uomQuantity(), posting, warehouseBalance));
    }

    /**
     * Fila de costo del producto; con fecha explícita valida la cronología del kárdex (R-24).
     * <p>
     * En egresos, transferencias y ajustes se lee ANTES que el stock: con READ COMMITTED el stock leído después es
     * igual o más reciente que el costo, así una lectura desfasada termina en conflicto de {@code @Version} (y se
     * reintenta) en lugar de un falso descuadre entre costo y stock.
     */
    private ProductCost productCost(Product product, Timing timing) {
        costs.insertIfAbsent(product.getId());
        ProductCost cost = costs.findById(product.getId()).orElseThrow();
        if (timing.explicit() && cost.getLastMovementAt() != null && timing.at().isBefore(cost.getLastMovementAt())) {
            throw new BusinessRuleException("BACKDATED_MOVEMENT",
                    "No se permiten movimientos anteriores al último movimiento de %s".formatted(product.getSku()));
        }
        return cost;
    }

    private Stock lockStock(Product product, Warehouse warehouse, Location location, Lot lot) {
        stocks.insertIfAbsent(product.getId(), warehouse.getId(), location.getId(), lot == null ? null : lot.getId());
        return existingStock(product, location, lot).orElseThrow();
    }

    private Optional<Stock> existingStock(Product product, Location location, Lot lot) {
        return lot == null
                ? stocks.findByProductIdAndLocationIdAndLotIsNull(product.getId(), location.getId())
                : stocks.findByProductIdAndLocationIdAndLotId(product.getId(), location.getId(), lot.getId());
    }

    /**
     * Asigna el egreso a filas de stock. Con ubicación/lote indicados se usa solo eso; si no, FEFO (R-18): primero lo
     * que vence antes y nunca lotes vencidos, salvo que {@code allowExpired}.
     */
    private List<Allocation> allocate(Product product, Warehouse warehouse, Long locationId, Long lotId,
                                      BigDecimal requested, boolean allowExpired, LocalDate today) {
        String where = warehouse.getCode();
        if (locationId != null) {
            where = references.activeLocation(locationId, warehouse).getCode();
        }
        if (lotId != null) {
            Lot lot = references.lotOf(product, lotId);
            if (lot.isExpiredOn(today) && !allowExpired) {
                throw new BusinessRuleException("LOT_EXPIRED", "El lote %s venció el %s; dalo de baja con un ajuste"
                        .formatted(lot.getLotNumber(), lot.getExpiryDate()));
            }
        }
        List<Stock> candidates = stocks.findFefoCandidates(product.getId(), warehouse.getId(), locationId, lotId,
                allowExpired || lotId != null, today);
        List<Allocation> allocations = new ArrayList<>();
        BigDecimal remaining = requested;
        for (Stock candidate : candidates) {
            if (remaining.signum() == 0) {
                break;
            }
            BigDecimal take = candidate.getQuantity().min(remaining);
            allocations.add(new Allocation(candidate, take));
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            throw new InsufficientStockException(product.getSku(), where, requested.subtract(remaining), requested);
        }
        return allocations;
    }

    private Lot resolveReceiptLot(Product product, String lotNumber, LocalDate expiryDate, LocalDate productionDate) {
        if (!product.isLotControlled()) {
            if (lotNumber != null && !lotNumber.isBlank()) {
                throw new BusinessRuleException("LOT_NOT_ALLOWED", "El producto %s no maneja lotes".formatted(product.getSku()));
            }
            return null;
        }
        if (lotNumber == null || lotNumber.isBlank()) {
            throw new BusinessRuleException("LOT_REQUIRED", "El producto %s exige lote".formatted(product.getSku()));
        }
        if (expiryDate == null) {
            throw new BusinessRuleException("EXPIRY_REQUIRED", "Indica la fecha de vencimiento del lote " + lotNumber);
        }
        if (productionDate != null && productionDate.isAfter(expiryDate)) {
            throw new BusinessRuleException("INVALID_LOT_DATES", "La fecha de producción es posterior al vencimiento");
        }
        lots.insertIfAbsent(product.getId(), lotNumber, expiryDate, productionDate);
        Lot lot = lots.findByProductIdAndLotNumber(product.getId(), lotNumber).orElseThrow();
        if (!Objects.equals(lot.getExpiryDate(), expiryDate)) {
            throw new BusinessRuleException("LOT_EXPIRY_MISMATCH", "El lote %s ya existe con vencimiento %s"
                    .formatted(lotNumber, lot.getExpiryDate()));
        }
        return lot;
    }

    private Lot resolveAdjustmentLot(Product product, AdjustmentLine line, boolean positive) {
        boolean lotGiven = line.lotId() != null || (line.lotNumber() != null && !line.lotNumber().isBlank());
        if (!product.isLotControlled()) {
            if (lotGiven) {
                throw new BusinessRuleException("LOT_NOT_ALLOWED", "El producto %s no maneja lotes".formatted(product.getSku()));
            }
            return null;
        }
        if (line.lotId() != null) {
            return references.lotOf(product, line.lotId());
        }
        if (!lotGiven) {
            throw new BusinessRuleException("LOT_REQUIRED", "El producto %s exige lote".formatted(product.getSku()));
        }
        return lots.findByProductIdAndLotNumber(product.getId(), line.lotNumber())
                .orElseGet(() -> {
                    if (!positive) {
                        throw new NotFoundException("Lote", line.lotNumber());
                    }
                    return resolveReceiptLot(product, line.lotNumber(), line.expiryDate(), null);
                });
    }

    private static BigDecimal positiveAdjustmentCost(ProductCost cost, BigDecimal requestedCost) {
        if (cost.getAverageCost().signum() > 0) {
            return cost.getAverageCost();
        }
        if (requestedCost != null) {
            return requestedCost;
        }
        throw new BusinessRuleException("COST_REQUIRED",
                "El producto no tiene costo promedio: indica el costo unitario del ajuste");
    }

    private static Quantity toBase(Product product, String uomCode, BigDecimal quantity) {
        String code = uomCode == null || uomCode.isBlank() ? product.getBaseUom().getCode() : uomCode;
        BigDecimal factor = product.factorFor(code).orElseThrow(() -> new BusinessRuleException("UNKNOWN_UOM",
                "El producto %s no tiene conversión para la unidad %s".formatted(product.getSku(), code)));
        BigDecimal base = quantity.multiply(factor).setScale(ProductCost.QUANTITY_SCALE, ProductCost.ROUNDING);
        if (base.signum() <= 0) {
            throw new BusinessRuleException("INVALID_QUANTITY", "La cantidad debe ser mayor que cero");
        }
        return new Quantity(base, code, quantity, factor);
    }

    private Timing timing(Instant requestedAt) {
        return requestedAt == null ? new Timing(Instant.now(clock), false) : new Timing(requestedAt, true);
    }

    private LocalDate today(Instant at) {
        return LocalDate.ofInstant(at, properties.inventory().timeZone());
    }

    private static <T> List<T> sortedByProduct(List<T> items, Function<T, Long> productId) {
        return items.stream().sorted(Comparator.comparing(productId)).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Cantidad en unidad base y tal como se digitó (unidad y cantidad del documento). */
    private record Quantity(BigDecimal base, String uomCode, BigDecimal uomQuantity, BigDecimal factor) {

        Quantity portion(BigDecimal baseQuantity) {
            if (baseQuantity.compareTo(base) == 0) {
                return this;
            }
            // Hacia arriba: una porción nunca queda en 0 en la unidad del documento.
            BigDecimal uomPart = baseQuantity.divide(factor, ProductCost.QUANTITY_SCALE, RoundingMode.UP);
            return new Quantity(baseQuantity, uomCode, uomPart, factor);
        }
    }

    private record Allocation(Stock stock, BigDecimal quantity) {
    }

    /** Momento del movimiento y si lo indicó quien llama (explícito) o es la hora del servidor. */
    private record Timing(Instant at, boolean explicit) {
    }
}
