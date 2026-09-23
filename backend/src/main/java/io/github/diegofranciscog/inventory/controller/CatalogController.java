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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.diegofranciscog.inventory.dto.catalog.CatalogResponses.CategoryResponse;
import io.github.diegofranciscog.inventory.dto.catalog.CatalogResponses.DocumentTypeResponse;
import io.github.diegofranciscog.inventory.dto.catalog.CatalogResponses.UnitResponse;
import io.github.diegofranciscog.inventory.dto.catalog.CategoryRequest;
import io.github.diegofranciscog.inventory.service.CatalogService;

@RestController
@RequestMapping("/api")
@Tag(name = "Catálogos")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalogService.categories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return catalogService.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public CategoryResponse updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return catalogService.updateCategory(id, request);
    }

    @GetMapping("/units")
    public List<UnitResponse> units() {
        return catalogService.units();
    }

    @GetMapping("/document-types")
    public List<DocumentTypeResponse> documentTypes() {
        return catalogService.documentTypes();
    }
}
