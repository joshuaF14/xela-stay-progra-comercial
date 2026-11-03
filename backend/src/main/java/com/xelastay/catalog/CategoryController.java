package com.xelastay.catalog;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.xelastay.catalog.dto.CatalogItemRequest;
import com.xelastay.catalog.dto.CategoryResponse;
import com.xelastay.profile.SystemRoles;
import com.xelastay.security.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** GET públicos (solo activas; un ADMIN puede pedir también las inactivas). El resto, solo ADMIN. */
@RestController
@RequestMapping("/api/v1/property-categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final CurrentUser currentUser;

    public CategoryController(CategoryService categoryService, CurrentUser currentUser) {
        this.categoryService = categoryService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<CategoryResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return categoryService.list(includeInactive && currentUser.hasRole(SystemRoles.ADMIN));
    }

    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable UUID id) {
        return categoryService.get(id, currentUser.hasRole(SystemRoles.ADMIN));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CatalogItemRequest request) {
        CategoryResponse category = categoryService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/property-categories/" + category.id())).body(category);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CatalogItemRequest request) {
        return categoryService.update(id, request);
    }

    /** Desactiva (active = false); no borra la fila. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        categoryService.deactivate(id);
    }
}
