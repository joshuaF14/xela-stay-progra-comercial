package com.xelastay.catalog;

import java.util.List;
import java.util.UUID;

import com.xelastay.catalog.dto.CatalogItemRequest;
import com.xelastay.catalog.dto.CategoryResponse;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Categorías de alojamiento. Lectura pública de las activas; alta, edición y desactivación solo ADMIN. */
@Service
public class CategoryService {

    private final PropertyCategoryRepository categories;

    public CategoryService(PropertyCategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(boolean includeInactive) {
        List<PropertyCategory> rows = includeInactive
                ? categories.findAllByOrderByNameAsc()
                : categories.findByActiveTrueOrderByNameAsc();
        return rows.stream().map(CategoryService::toResponse).toList();
    }

    /** Una categoría inactiva solo es visible para quien puede administrarla (404 para el resto). */
    @Transactional(readOnly = true)
    public CategoryResponse get(UUID id, boolean includeInactive) {
        PropertyCategory category = find(id);
        if (!category.isActive() && !includeInactive) {
            throw notFound();
        }
        return toResponse(category);
    }

    @Transactional
    public CategoryResponse create(CatalogItemRequest request) {
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw duplicate();
        }
        boolean active = request.active() == null || request.active();
        return toResponse(save(PropertyCategory.create(name, blankToNull(request.description()), active)));
    }

    @Transactional
    public CategoryResponse update(UUID id, CatalogItemRequest request) {
        PropertyCategory category = find(id);
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw duplicate();
        }
        boolean active = request.active() == null ? category.isActive() : request.active();
        category.update(name, blankToNull(request.description()), active);
        return toResponse(save(category));
    }

    /** Desactiva sin borrar: los alojamientos que ya la usan la conservan. */
    @Transactional
    public void deactivate(UUID id) {
        PropertyCategory category = find(id);
        category.deactivate();
        categories.save(category);
    }

    private PropertyCategory save(PropertyCategory category) {
        try {
            return categories.saveAndFlush(category);
        } catch (DataIntegrityViolationException ex) {
            throw duplicate();
        }
    }

    private PropertyCategory find(UUID id) {
        return categories.findById(id).orElseThrow(CategoryService::notFound);
    }

    static CategoryResponse toResponse(PropertyCategory category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(),
                category.isActive(), category.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada");
    }

    private static ResponseStatusException duplicate() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una categoría con ese nombre");
    }
}
