package io.github.diegofranciscog.inventory.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.OptimisticLockException;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.diegofranciscog.inventory.config.AppProperties;
import io.github.diegofranciscog.inventory.dto.common.PageResponse;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.AddLineRequest;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.CountLineRequest;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.CreateRequest;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.CycleCountResponse;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.ReviewRequest;
import io.github.diegofranciscog.inventory.dto.count.CycleCountDtos.SummaryResponse;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.AdjustmentLine;
import io.github.diegofranciscog.inventory.dto.movement.MovementRequests.AdjustmentRequest;
import io.github.diegofranciscog.inventory.entity.AppUser;
import io.github.diegofranciscog.inventory.entity.CycleCount;
import io.github.diegofranciscog.inventory.entity.CycleCountLine;
import io.github.diegofranciscog.inventory.entity.CycleCountStatus;
import io.github.diegofranciscog.inventory.entity.InventoryMovement;
import io.github.diegofranciscog.inventory.entity.Location;
import io.github.diegofranciscog.inventory.entity.Lot;
import io.github.diegofranciscog.inventory.entity.Product;
import io.github.diegofranciscog.inventory.entity.ProductCost;
import io.github.diegofranciscog.inventory.entity.Stock;
import io.github.diegofranciscog.inventory.entity.Warehouse;
import io.github.diegofranciscog.inventory.exception.BusinessRuleException;
import io.github.diegofranciscog.inventory.exception.NotFoundException;
import io.github.diegofranciscog.inventory.mapper.CycleCountMapper;
import io.github.diegofranciscog.inventory.repository.CycleCountRepository;
import io.github.diegofranciscog.inventory.repository.LotRepository;
import io.github.diegofranciscog.inventory.repository.ProductCostRepository;
import io.github.diegofranciscog.inventory.repository.StockRepository;

/**
 * Conteo cíclico con diferencias, aprobación y auditoría (R-20, R-21):
 * <ol>
 *   <li>Se crea para una bodega (filtros opcionales por pasillo y clase ABC) con una línea por existencia.</li>
 *   <li>El contador registra cantidades sin ver las del sistema (conteo ciego); el sistema fija su cantidad al contar.</li>
 *   <li>Al enviarlo, las diferencias se hacen visibles.</li>
 *   <li>Un SUPERVISOR distinto de quien contó aprueba: se genera un AJUSTE enlazado al conteo, en la misma transacción.</li>
 * </ol>
 */
@Service
public class CycleCountService {

    private final CycleCountRepository counts;
    private final StockRepository stocks;
    private final LotRepository lots;
    private final ProductCostRepository costs;
    private final ReferenceResolver references;
    private final WarehouseAccessService access;
    private final MovementPostingService posting;
    private final AuditService audit;
    private final AppProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public CycleCountService(CycleCountRepository counts, StockRepository stocks, LotRepository lots,
                             ProductCostRepository costs, ReferenceResolver references, WarehouseAccessService access,
                             MovementPostingService posting, AuditService audit, AppProperties properties, Clock clock,
                             PlatformTransactionManager transactionManager) {
        this.counts = counts;
        this.stocks = stocks;
        this.lots = lots;
        this.costs = costs;
        this.references = references;
        this.access = access;
        this.posting = posting;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public CycleCountResponse create(CreateRequest request, Long userId) {
        AppUser user = references.activeUser(userId);
        Warehouse warehouse = references.activeWarehouse(request.warehouseId());
        access.requireCanOperate(user, warehouse);
        String number = "CC-%06d".formatted(counts.nextNumber());
        CycleCount count = new CycleCount(number, warehouse, request.aisle(), request.abcClass(), request.notes(), user,
                Instant.now(clock));
        for (Stock stock : stocks.findForCycleCount(warehouse.getId(), request.aisle(), request.abcClass())) {
            count.addLine(stock.getLocation(), stock.getProduct(), stock.getLot());
        }
        if (count.getLines().isEmpty()) {
            throw new BusinessRuleException("NOTHING_TO_COUNT", "No hay existencias que contar con esos filtros");
        }
        counts.save(count);
        audit.record("COUNT_CREATED", "CycleCount", count.getId(),
                Map.of("number", number, "warehouse", warehouse.getCode(), "lines", count.getLines().size()));
        return CycleCountMapper.toResponse(count);
    }

    @Transactional(readOnly = true)
    public CycleCountResponse get(Long id) {
        return CycleCountMapper.toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<SummaryResponse> search(Long warehouseId, CycleCountStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Pagination.clamp(size, properties),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(counts.search(warehouseId, status, pageable), CycleCountMapper::toSummary);
    }

    /** Registra la cantidad contada; la cantidad del sistema se toma en este instante y el contador no la ve. */
    @Transactional
    public CycleCountResponse registerCount(Long countId, Long lineId, CountLineRequest request, Long userId) {
        AppUser user = references.activeUser(userId);
        CycleCount count = load(countId);
        access.requireCanOperate(user, count.getWarehouse());
        CycleCountLine line = count.getLines().stream()
                .filter(candidate -> candidate.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Línea de conteo", lineId));
        register(line, request.countedQuantity(), request.note(), user);
        return CycleCountMapper.toResponse(count);
    }

    /** Producto encontrado donde el sistema no lo esperaba: se agrega como línea nueva ya contada. */
    @Transactional
    public CycleCountResponse addLine(Long countId, AddLineRequest request, Long userId) {
        AppUser user = references.activeUser(userId);
        CycleCount count = load(countId);
        access.requireCanOperate(user, count.getWarehouse());
        Location location = references.activeLocation(request.locationId(), count.getWarehouse());
        Product product = references.activeProduct(request.productId());
        Lot lot = resolveLot(product, request.lotNumber(), request.expiryDate());
        CycleCountLine line = count.addLine(location, product, lot);
        register(line, request.countedQuantity(), request.note(), user);
        counts.saveAndFlush(count);
        return CycleCountMapper.toResponse(count);
    }

    @Transactional
    public CycleCountResponse submit(Long countId, Long userId) {
        AppUser user = references.activeUser(userId);
        CycleCount count = load(countId);
        access.requireCanOperate(user, count.getWarehouse());
        count.submit(user, Instant.now(clock));
        audit.record("COUNT_SUBMITTED", "CycleCount", count.getId(), Map.of("number", count.getNumber(),
                "linesWithDifference", count.getLines().stream().filter(CycleCountLine::hasDifference).count()));
        return CycleCountMapper.toResponse(count);
    }

    /**
     * Aprueba el conteo y registra el ajuste por las diferencias en la misma transacción. Se reintenta si otra operación
     * modificó el mismo stock o costo mientras tanto.
     */
    @Retryable(includes = {ConcurrencyFailureException.class, OptimisticLockException.class},
            maxRetries = 4, delay = 25, jitter = 25, multiplier = 2, maxDelay = 400)
    public CycleCountResponse approve(Long countId, ReviewRequest request, Long userId) {
        return transaction.execute(status -> {
            AppUser reviewer = references.activeUser(userId);
            CycleCount count = load(countId);
            access.requireCanOperate(reviewer, count.getWarehouse());
            count.requireStatus(CycleCountStatus.SUBMITTED);
            count.requireSegregationOfDuties(reviewer);
            Instant now = Instant.now(clock);
            InventoryMovement adjustment = count.hasDifferences()
                    ? posting.adjust(adjustmentFor(count), reviewer.getId(), null, count.getId())
                    : null;
            count.approve(reviewer, now, notes(request), adjustment);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("number", count.getNumber());
            details.put("adjustment", adjustment == null ? "sin diferencias" : adjustment.getNumber());
            details.put("differenceValue", count.getLines().stream()
                    .map(CycleCountLine::differenceValue).reduce(BigDecimal.ZERO, BigDecimal::add));
            audit.record("COUNT_APPROVED", "CycleCount", count.getId(), details);
            return CycleCountMapper.toResponse(count);
        });
    }

    @Transactional
    public CycleCountResponse reject(Long countId, ReviewRequest request, Long userId) {
        AppUser reviewer = references.activeUser(userId);
        CycleCount count = load(countId);
        access.requireCanOperate(reviewer, count.getWarehouse());
        count.reject(reviewer, Instant.now(clock), notes(request));
        audit.record("COUNT_REJECTED", "CycleCount", count.getId(),
                Map.of("number", count.getNumber(), "notes", String.valueOf(count.getReviewNotes())));
        return CycleCountMapper.toResponse(count);
    }

    @Transactional
    public CycleCountResponse cancel(Long countId, Long userId) {
        AppUser user = references.activeUser(userId);
        CycleCount count = load(countId);
        access.requireCanOperate(user, count.getWarehouse());
        count.cancel();
        audit.record("COUNT_CANCELLED", "CycleCount", count.getId(), Map.of("number", count.getNumber()));
        return CycleCountMapper.toResponse(count);
    }

    private void register(CycleCountLine line, BigDecimal counted, String note, AppUser user) {
        Product product = line.getProduct();
        Lot lot = line.getLot();
        BigDecimal systemQuantity = (lot == null
                ? stocks.findByProductIdAndLocationIdAndLotIsNull(product.getId(), line.getLocation().getId())
                : stocks.findByProductIdAndLocationIdAndLotId(product.getId(), line.getLocation().getId(), lot.getId()))
                .map(Stock::getQuantity)
                .orElse(BigDecimal.ZERO);
        BigDecimal unitCost = costs.findById(product.getId()).map(ProductCost::getAverageCost).orElse(BigDecimal.ZERO);
        line.registerCount(counted, systemQuantity, unitCost, user, Instant.now(clock), note);
    }

    private AdjustmentRequest adjustmentFor(CycleCount count) {
        List<AdjustmentLine> adjustmentLines = count.getLines().stream()
                .filter(CycleCountLine::hasDifference)
                .map(line -> new AdjustmentLine(
                        line.getProduct().getId(),
                        line.getLocation().getId(),
                        line.getLot() == null ? null : line.getLot().getId(),
                        null,
                        null,
                        line.getDifference(),
                        line.getUnitCost()))
                .toList();
        return new AdjustmentRequest(count.getWarehouse().getId(), "CC", count.getNumber(),
                "Ajuste por conteo cíclico " + count.getNumber(), adjustmentLines);
    }

    private Lot resolveLot(Product product, String lotNumber, LocalDate expiryDate) {
        boolean lotGiven = lotNumber != null && !lotNumber.isBlank();
        if (!product.isLotControlled()) {
            if (lotGiven) {
                throw new BusinessRuleException("LOT_NOT_ALLOWED", "El producto %s no maneja lotes".formatted(product.getSku()));
            }
            return null;
        }
        if (!lotGiven) {
            throw new BusinessRuleException("LOT_REQUIRED", "El producto %s exige lote".formatted(product.getSku()));
        }
        return lots.findByProductIdAndLotNumber(product.getId(), lotNumber).orElseGet(() -> {
            if (expiryDate == null) {
                throw new BusinessRuleException("EXPIRY_REQUIRED", "Indica la fecha de vencimiento del lote " + lotNumber);
            }
            lots.insertIfAbsent(product.getId(), lotNumber, expiryDate, null);
            return lots.findByProductIdAndLotNumber(product.getId(), lotNumber).orElseThrow();
        });
    }

    private CycleCount load(Long id) {
        return counts.findWithLinesById(id).orElseThrow(() -> new NotFoundException("Conteo", id));
    }

    private static String notes(ReviewRequest request) {
        return request == null || request.notes() == null || request.notes().isBlank() ? null : request.notes().strip();
    }
}
