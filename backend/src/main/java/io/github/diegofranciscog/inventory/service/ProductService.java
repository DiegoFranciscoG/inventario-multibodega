package io.github.diegofranciscog.inventory.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.diegofranciscog.inventory.config.AppProperties;
import io.github.diegofranciscog.inventory.dto.catalog.CatalogResponses.ProductResponse;
import io.github.diegofranciscog.inventory.dto.catalog.ProductRequest;
import io.github.diegofranciscog.inventory.dto.common.PageResponse;
import io.github.diegofranciscog.inventory.dto.storage.StorageRequests.MinStockRequest;
import io.github.diegofranciscog.inventory.dto.storage.StorageResponses.MinStockResponse;
import io.github.diegofranciscog.inventory.entity.AbcClass;
import io.github.diegofranciscog.inventory.entity.Category;
import io.github.diegofranciscog.inventory.entity.Product;
import io.github.diegofranciscog.inventory.entity.StockMinRule;
import io.github.diegofranciscog.inventory.entity.UnitOfMeasure;
import io.github.diegofranciscog.inventory.entity.Warehouse;
import io.github.diegofranciscog.inventory.exception.BusinessRuleException;
import io.github.diegofranciscog.inventory.exception.ConflictException;
import io.github.diegofranciscog.inventory.exception.NotFoundException;
import io.github.diegofranciscog.inventory.gs1.Gtin;
import io.github.diegofranciscog.inventory.mapper.CatalogMapper;
import io.github.diegofranciscog.inventory.mapper.StorageMapper;
import io.github.diegofranciscog.inventory.repository.CategoryRepository;
import io.github.diegofranciscog.inventory.repository.ProductCostRepository;
import io.github.diegofranciscog.inventory.repository.ProductRepository;
import io.github.diegofranciscog.inventory.repository.ProductUomConversionRepository;
import io.github.diegofranciscog.inventory.repository.StockMinRuleRepository;
import io.github.diegofranciscog.inventory.repository.UnitOfMeasureRepository;

@Service
public class ProductService {

    private final ProductRepository products;
    private final ProductUomConversionRepository conversions;
    private final CategoryRepository categories;
    private final UnitOfMeasureRepository units;
    private final ProductCostRepository costs;
    private final StockMinRuleRepository minRules;
    private final ReferenceResolver references;
    private final AuditService audit;
    private final AppProperties properties;

    public ProductService(ProductRepository products, ProductUomConversionRepository conversions,
                          CategoryRepository categories, UnitOfMeasureRepository units, ProductCostRepository costs,
                          StockMinRuleRepository minRules, ReferenceResolver references, AuditService audit,
                          AppProperties properties) {
        this.products = products;
        this.conversions = conversions;
        this.categories = categories;
        this.units = units;
        this.costs = costs;
        this.minRules = minRules;
        this.references = references;
        this.audit = audit;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String text, Long categoryId, int page, int size) {
        String filter = text == null || text.isBlank() ? null : text.strip();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Pagination.clamp(size, properties), Sort.by("sku"));
        return PageResponse.from(products.search(filter, categoryId, pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return toResponse(references.product(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (products.existsBySku(request.sku())) {
            throw new ConflictException("DUPLICATE_SKU", "Ya existe un producto con SKU " + request.sku());
        }
        String gtin = normalizeGtin(request.gtin());
        requireUniqueGtins(gtin, request.conversions(), null);
        Product product = new Product(request.sku(), gtin, request.name().strip(), request.description(),
                category(request.categoryId()), unit(request.baseUomCode()), Boolean.TRUE.equals(request.lotControlled()),
                request.abcClass() == null ? AbcClass.C : request.abcClass());
        applyConversions(product, request.conversions());
        products.saveAndFlush(product);
        costs.insertIfAbsent(product.getId());
        audit.record("PRODUCT_CREATED", "Product", product.getId(), Map.of("sku", product.getSku()));
        return toResponse(product);
    }

    /** El SKU, la unidad base y el control de lote no cambian: alterarían el significado del kárdex existente. */
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = references.product(id);
        if (!product.getSku().equals(request.sku())) {
            throw new BusinessRuleException("IMMUTABLE_SKU", "El SKU no se puede cambiar");
        }
        boolean lotControlled = Boolean.TRUE.equals(request.lotControlled());
        if (!product.getBaseUom().getCode().equals(request.baseUomCode()) || product.isLotControlled() != lotControlled) {
            throw new BusinessRuleException("IMMUTABLE_PRODUCT_FIELDS",
                    "La unidad base y el control de lote no se pueden cambiar después de crear el producto");
        }
        String gtin = normalizeGtin(request.gtin());
        requireUniqueGtins(gtin, request.conversions(), product);
        product.update(gtin, request.name().strip(), request.description(), category(request.categoryId()),
                request.abcClass() == null ? product.getAbcClass() : request.abcClass(),
                request.active() == null || request.active());
        product.getConversions().clear();
        products.flush();
        applyConversions(product, request.conversions());
        audit.record("PRODUCT_UPDATED", "Product", product.getId(), Map.of("sku", product.getSku()));
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public List<MinStockResponse> minimums(Long productId) {
        references.product(productId);
        return minRules.findByProduct(productId).stream().map(StorageMapper::toResponse).toList();
    }

    @Transactional
    public MinStockResponse setMinimum(Long productId, MinStockRequest request) {
        Product product = references.product(productId);
        Warehouse warehouse = references.warehouse(request.warehouseId());
        StockMinRule rule = minRules.findByProductIdAndWarehouseId(productId, warehouse.getId())
                .orElseGet(() -> new StockMinRule(product, warehouse, request.minQuantity()));
        rule.changeMinimum(request.minQuantity());
        minRules.save(rule);
        audit.record("MIN_STOCK_SET", "Product", productId,
                Map.of("warehouse", warehouse.getCode(), "minQuantity", request.minQuantity()));
        return StorageMapper.toResponse(rule);
    }

    private void applyConversions(Product product, List<ProductRequest.Conversion> requested) {
        if (requested == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (ProductRequest.Conversion conversion : requested) {
            if (conversion.uomCode().equals(product.getBaseUom().getCode())) {
                throw new BusinessRuleException("INVALID_CONVERSION", "La unidad base no necesita conversión");
            }
            if (!seen.add(conversion.uomCode())) {
                throw new BusinessRuleException("DUPLICATE_CONVERSION", "Unidad repetida: " + conversion.uomCode());
            }
            product.addConversion(unit(conversion.uomCode()), conversion.factor(), normalizeGtin(conversion.gtin()));
        }
    }

    /** Un GTIN identifica una sola cosa: ni otro producto ni otro empaque pueden repetirlo (R-11, R-12). */
    private void requireUniqueGtins(String productGtin, List<ProductRequest.Conversion> requested, Product current) {
        Set<String> gtins = new HashSet<>();
        if (productGtin != null) {
            gtins.add(productGtin);
            boolean taken = current == null ? products.existsByGtin(productGtin)
                    : products.existsByGtinAndIdNot(productGtin, current.getId());
            if (taken || conversionOwnedByOther(productGtin, current)) {
                throw new ConflictException("DUPLICATE_GTIN", "El GTIN " + productGtin + " ya está registrado");
            }
        }
        if (requested != null) {
            for (ProductRequest.Conversion conversion : requested) {
                String gtin = normalizeGtin(conversion.gtin());
                if (gtin == null) {
                    continue;
                }
                if (!gtins.add(gtin) || products.existsByGtin(gtin) || conversionOwnedByOther(gtin, current)) {
                    throw new ConflictException("DUPLICATE_GTIN", "El GTIN " + gtin + " ya está registrado");
                }
            }
        }
    }

    private boolean conversionOwnedByOther(String gtin, Product current) {
        return conversions.findByGtin(gtin)
                .map(conversion -> current == null || !conversion.getProduct().getId().equals(current.getId()))
                .orElse(false);
    }

    private static String normalizeGtin(String gtin) {
        return gtin == null || gtin.isBlank() ? null : Gtin.of(gtin).value();
    }

    private Category category(Long id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Categoría", id));
    }

    private UnitOfMeasure unit(String code) {
        return units.findById(code).orElseThrow(() -> new NotFoundException("Unidad de medida", code));
    }

    private ProductResponse toResponse(Product product) {
        return CatalogMapper.toResponse(product, costs.findById(product.getId()).orElse(null));
    }
}
