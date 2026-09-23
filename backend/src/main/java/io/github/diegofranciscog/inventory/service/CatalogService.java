package io.github.diegofranciscog.inventory.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.diegofranciscog.inventory.dto.catalog.CatalogResponses.CategoryResponse;
import io.github.diegofranciscog.inventory.dto.catalog.CatalogResponses.DocumentTypeResponse;
import io.github.diegofranciscog.inventory.dto.catalog.CatalogResponses.UnitResponse;
import io.github.diegofranciscog.inventory.dto.catalog.CategoryRequest;
import io.github.diegofranciscog.inventory.entity.Category;
import io.github.diegofranciscog.inventory.exception.BusinessRuleException;
import io.github.diegofranciscog.inventory.exception.ConflictException;
import io.github.diegofranciscog.inventory.exception.NotFoundException;
import io.github.diegofranciscog.inventory.mapper.CatalogMapper;
import io.github.diegofranciscog.inventory.repository.CategoryRepository;
import io.github.diegofranciscog.inventory.repository.DocumentTypeRepository;
import io.github.diegofranciscog.inventory.repository.UnitOfMeasureRepository;

/** Catálogos: categorías (editables), unidades UNECE y tipos de documento (cargados por migración). */
@Service
public class CatalogService {

    private final CategoryRepository categories;
    private final UnitOfMeasureRepository units;
    private final DocumentTypeRepository documentTypes;
    private final AuditService audit;

    public CatalogService(CategoryRepository categories, UnitOfMeasureRepository units,
                          DocumentTypeRepository documentTypes, AuditService audit) {
        this.categories = categories;
        this.units = units;
        this.documentTypes = documentTypes;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return categories.findAllByOrderByNameAsc().stream().map(CatalogMapper::toResponse).toList();
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categories.existsByCode(request.code())) {
            throw new ConflictException("DUPLICATE_CATEGORY", "Ya existe la categoría " + request.code());
        }
        Category category = categories.save(new Category(request.code(), request.name().strip()));
        audit.record("CATEGORY_CREATED", "Category", category.getId(), Map.of("code", category.getCode()));
        return CatalogMapper.toResponse(category);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = categories.findById(id).orElseThrow(() -> new NotFoundException("Categoría", id));
        if (!category.getCode().equals(request.code())) {
            throw new BusinessRuleException("IMMUTABLE_CODE", "El código de la categoría no se puede cambiar");
        }
        category.update(request.name().strip(), request.active() == null || request.active());
        audit.record("CATEGORY_UPDATED", "Category", category.getId(), Map.of("code", category.getCode()));
        return CatalogMapper.toResponse(category);
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> units() {
        return units.findAllByOrderByCodeAsc().stream().map(CatalogMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentTypeResponse> documentTypes() {
        return documentTypes.findAllByOrderBySourceDescCodeAsc().stream().map(CatalogMapper::toResponse).toList();
    }
}
