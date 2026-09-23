package io.github.diegofranciscog.inventory.controller;

import java.util.List;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.diegofranciscog.inventory.dto.catalog.CatalogResponses.ProductResponse;
import io.github.diegofranciscog.inventory.dto.catalog.ProductRequest;
import io.github.diegofranciscog.inventory.dto.common.PageResponse;
import io.github.diegofranciscog.inventory.dto.storage.StorageRequests.MinStockRequest;
import io.github.diegofranciscog.inventory.dto.storage.StorageResponses.LotResponse;
import io.github.diegofranciscog.inventory.dto.storage.StorageResponses.MinStockResponse;
import io.github.diegofranciscog.inventory.service.ProductService;
import io.github.diegofranciscog.inventory.service.StockService;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Productos")
public class ProductController {

    private final ProductService productService;
    private final StockService stockService;

    public ProductController(ProductService productService, StockService stockService) {
        this.productService = productService;
        this.stockService = stockService;
    }

    @GetMapping
    public PageResponse<ProductResponse> search(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) Long categoryId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return productService.search(q, categoryId, page, size);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return productService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @GetMapping("/{id}/lots")
    public List<LotResponse> lots(@PathVariable Long id) {
        return stockService.lots(id);
    }

    @GetMapping("/{id}/minimums")
    public List<MinStockResponse> minimums(@PathVariable Long id) {
        return productService.minimums(id);
    }

    @PutMapping("/{id}/minimums")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public MinStockResponse setMinimum(@PathVariable Long id, @Valid @RequestBody MinStockRequest request) {
        return productService.setMinimum(id, request);
    }
}
