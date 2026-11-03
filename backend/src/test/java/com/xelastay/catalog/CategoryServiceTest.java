package com.xelastay.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.xelastay.audit.AuditService;
import com.xelastay.catalog.dto.CatalogItemRequest;
import com.xelastay.catalog.dto.CategoryResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private PropertyCategoryRepository categories;

    @Mock
    private AuditService audit;

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categories, audit);
    }

    @Test
    void createIsActiveByDefaultAndTrimsTheName() {
        when(categories.existsByNameIgnoreCase("Casa")).thenReturn(false);
        when(categories.saveAndFlush(any(PropertyCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = service.create(new CatalogItemRequest("  Casa ", " ", null));

        assertThat(response.name()).isEqualTo("Casa");
        assertThat(response.description()).isNull();
        assertThat(response.active()).isTrue();
    }

    @Test
    void duplicateNameIgnoringCaseIs409() {
        when(categories.existsByNameIgnoreCase("casa")).thenReturn(true);

        assertStatus(() -> service.create(new CatalogItemRequest("casa", null, null)), HttpStatus.CONFLICT);
        verify(categories, never()).saveAndFlush(any());
    }

    @Test
    void uniqueViolationRaceIsAlso409() {
        when(categories.existsByNameIgnoreCase("Casa")).thenReturn(false);
        when(categories.saveAndFlush(any(PropertyCategory.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertStatus(() -> service.create(new CatalogItemRequest("Casa", null, null)), HttpStatus.CONFLICT);
    }

    @Test
    void updateWithoutActiveKeepsTheCurrentValue() {
        PropertyCategory category = category("Hotel", false);
        when(categories.findById(category.getId())).thenReturn(Optional.of(category));
        when(categories.existsByNameIgnoreCaseAndIdNot("Hotel boutique", category.getId())).thenReturn(false);
        when(categories.saveAndFlush(category)).thenReturn(category);

        CategoryResponse response = service.update(category.getId(),
                new CatalogItemRequest("Hotel boutique", "Pequeño", null));

        assertThat(response.name()).isEqualTo("Hotel boutique");
        assertThat(response.active()).isFalse();
    }

    @Test
    void inactiveCategoryIsHiddenUnlessAdmin() {
        PropertyCategory category = category("Hostal", false);
        when(categories.findById(category.getId())).thenReturn(Optional.of(category));

        assertStatus(() -> service.get(category.getId(), false), HttpStatus.NOT_FOUND);
        assertThat(service.get(category.getId(), true).name()).isEqualTo("Hostal");
    }

    @Test
    void deactivateKeepsTheRow() {
        PropertyCategory category = category("Cabaña", true);
        when(categories.findById(category.getId())).thenReturn(Optional.of(category));

        service.deactivate(category.getId());

        assertThat(category.isActive()).isFalse();
        verify(categories).save(category);
        verify(categories, never()).delete(any());
        verify(audit).recordAction("CATEGORY_DEACTIVATED", "property_category", category.getId(),
                Map.of("active", true), Map.of("active", false), null);
    }

    private static PropertyCategory category(String name, boolean active) {
        PropertyCategory category = PropertyCategory.create(name, null, active);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        return category;
    }

    private static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(status));
    }
}
