package io.github.diegofranciscog.inventory.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.diegofranciscog.inventory.dto.report.ReportResponses.ScanFormat;
import io.github.diegofranciscog.inventory.dto.report.ReportResponses.ScanResponse;
import io.github.diegofranciscog.inventory.dto.report.ReportResponses.ScanType;
import io.github.diegofranciscog.inventory.dto.storage.StorageResponses.StockResponse;
import io.github.diegofranciscog.inventory.entity.Location;
import io.github.diegofranciscog.inventory.entity.Lot;
import io.github.diegofranciscog.inventory.entity.Product;
import io.github.diegofranciscog.inventory.entity.ProductUomConversion;
import io.github.diegofranciscog.inventory.exception.BusinessRuleException;
import io.github.diegofranciscog.inventory.gs1.Gs1Data;
import io.github.diegofranciscog.inventory.gs1.Gs1Parser;
import io.github.diegofranciscog.inventory.gs1.Gtin;
import io.github.diegofranciscog.inventory.mapper.CatalogMapper;
import io.github.diegofranciscog.inventory.mapper.StorageMapper;
import io.github.diegofranciscog.inventory.repository.LocationRepository;
import io.github.diegofranciscog.inventory.repository.LotRepository;
import io.github.diegofranciscog.inventory.repository.ProductCostRepository;
import io.github.diegofranciscog.inventory.repository.ProductRepository;
import io.github.diegofranciscog.inventory.repository.ProductUomConversionRepository;
import io.github.diegofranciscog.inventory.repository.StockRepository;

/**
 * Interpreta lo que lee la cámara (R-14): QR interno de ubicación, cadena GS1 / GS1 Digital Link (GTIN + lote +
 * vencimiento), GTIN simple o SKU, y devuelve el producto o la ubicación con su stock.
 */
@Service
@Transactional(readOnly = true)
public class ScanService {

    private static final int MAX_CODE_LENGTH = 512;
    private static final int MAX_STOCK_ROWS = 50;
    private static final String LOCATION_PREFIX = "LOC:";

    private final ProductRepository products;
    private final ProductUomConversionRepository conversions;
    private final ProductCostRepository costs;
    private final LotRepository lots;
    private final LocationRepository locations;
    private final StockRepository stocks;

    public ScanService(ProductRepository products, ProductUomConversionRepository conversions, ProductCostRepository costs,
                       LotRepository lots, LocationRepository locations, StockRepository stocks) {
        this.products = products;
        this.conversions = conversions;
        this.costs = costs;
        this.lots = lots;
        this.locations = locations;
        this.stocks = stocks;
    }

    public ScanResponse resolve(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new BusinessRuleException("EMPTY_CODE", "El código está vacío");
        }
        if (rawCode.length() > MAX_CODE_LENGTH) {
            throw new BusinessRuleException("CODE_TOO_LONG", "El código supera los " + MAX_CODE_LENGTH + " caracteres");
        }
        String code = rawCode.strip();
        if (code.regionMatches(true, 0, LOCATION_PREFIX, 0, LOCATION_PREFIX.length())) {
            return resolveLocation(code);
        }
        Optional<Gs1Data> gs1 = Gs1Parser.parse(code).filter(Gs1Data::hasGtin);
        if (gs1.isPresent()) {
            ScanFormat format = code.regionMatches(true, 0, "http", 0, 4) ? ScanFormat.GS1_DIGITAL_LINK
                    : ScanFormat.GS1_ELEMENT_STRING;
            return resolveGtin(code, format, gs1.get().gtin(), gs1.get().lotNumber(), gs1.get().expiryDate());
        }
        Optional<Gtin> gtin = Gtin.parse(code);
        if (gtin.isPresent()) {
            return resolveGtin(code, ScanFormat.GTIN, gtin.get(), null, null);
        }
        return products.findBySku(code.toUpperCase(Locale.ROOT))
                .map(product -> productResponse(code, ScanFormat.SKU, product, null, null, null, null))
                .orElseGet(() -> unknown(code, ScanFormat.UNKNOWN));
    }

    private ScanResponse resolveLocation(String code) {
        String[] parts = code.split(":", 3);
        if (parts.length < 3) {
            return unknown(code, ScanFormat.LOCATION_QR);
        }
        Optional<Location> location = locations.findByWarehouseCodeAndCode(parts[1].toUpperCase(Locale.ROOT),
                parts[2].toUpperCase(Locale.ROOT));
        if (location.isEmpty()) {
            return unknown(code, ScanFormat.LOCATION_QR);
        }
        List<StockResponse> stock = stockOf(null, location.get().getId());
        return new ScanResponse(ScanType.LOCATION, ScanFormat.LOCATION_QR, code, null, null, null, null, null, null,
                StorageMapper.toResponse(location.get()), stock);
    }

    /** Busca el GTIN en productos (unidad base) y en empaques (GTIN-14 de caja, R-12). */
    private ScanResponse resolveGtin(String code, ScanFormat format, Gtin gtin, String lotNumber, LocalDate expiry) {
        Optional<Product> product = products.findByGtin(gtin.value());
        if (product.isPresent()) {
            return productResponse(code, format, product.get(), null, null, lotNumber, expiry);
        }
        Optional<ProductUomConversion> conversion = conversions.findByGtin(gtin.value());
        if (conversion.isPresent()) {
            Product packaged = products.findWithDetailsById(conversion.get().getProduct().getId()).orElseThrow();
            return productResponse(code, format, packaged, conversion.get().getUom().getCode(),
                    conversion.get().getFactor(), lotNumber, expiry);
        }
        return unknown(code, format);
    }

    private ScanResponse productResponse(String code, ScanFormat format, Product product, String uomCode,
                                         BigDecimal factor, String lotNumber, LocalDate expiry) {
        Long lotId = null;
        LocalDate lotExpiry = expiry;
        if (lotNumber != null) {
            Optional<Lot> lot = lots.findByProductIdAndLotNumber(product.getId(), lotNumber);
            if (lot.isPresent()) {
                lotId = lot.get().getId();
                lotExpiry = lot.get().getExpiryDate();
            }
        }
        return new ScanResponse(ScanType.PRODUCT, format, code,
                CatalogMapper.toResponse(product, costs.findById(product.getId()).orElse(null)),
                uomCode == null ? product.getBaseUom().getCode() : uomCode,
                factor == null ? BigDecimal.ONE : factor,
                lotNumber, lotExpiry, lotId, null, stockOf(product.getId(), null));
    }

    private List<StockResponse> stockOf(Long productId, Long locationId) {
        PageRequest page = PageRequest.of(0, MAX_STOCK_ROWS, Sort.by("warehouse.code", "location.code"));
        return stocks.search(null, productId, locationId, true, page).map(StorageMapper::toResponse).getContent();
    }

    private ScanResponse unknown(String code, ScanFormat format) {
        return new ScanResponse(ScanType.UNKNOWN, format, code, null, null, null, null, null, null, null, List.of());
    }
}
