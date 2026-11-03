package com.xelastay.property;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.xelastay.catalog.dto.AmenityResponse;
import com.xelastay.common.PageResponse;
import com.xelastay.profile.SystemRoles;
import com.xelastay.property.dto.PropertyAmenitiesRequest;
import com.xelastay.property.dto.PropertyDetailResponse;
import com.xelastay.property.dto.PropertyRequest;
import com.xelastay.property.dto.PropertyStatusRequest;
import com.xelastay.property.dto.PropertySummaryResponse;
import com.xelastay.security.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Búsqueda y detalle públicos; alta solo HOST; edición, estado, borrado y amenidades por el anfitrión del
 * alojamiento o un ADMIN (el servicio verifica la propiedad del recurso).
 */
@RestController
@RequestMapping("/api/v1")
public class PropertyController {

    private final PropertyService propertyService;
    private final CurrentUser currentUser;

    public PropertyController(PropertyService propertyService, CurrentUser currentUser) {
        this.propertyService = propertyService;
        this.currentUser = currentUser;
    }

    @GetMapping("/properties")
    public PageResponse<PropertySummaryResponse> search(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String municipality,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer guests,
            @RequestParam(required = false) List<UUID> amenityIds,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        PropertySearch filter = new PropertySearch(department, municipality, categoryId, minPrice, maxPrice, guests,
                amenityIds, q);
        return propertyService.search(filter, page, size, sort);
    }

    @GetMapping("/properties/{id}")
    public PropertyDetailResponse get(@PathVariable UUID id) {
        return propertyService.get(id, currentUser.id().orElse(null), isAdmin());
    }

    @PostMapping("/properties")
    @PreAuthorize("hasRole('HOST')")
    public ResponseEntity<PropertyDetailResponse> create(@Valid @RequestBody PropertyRequest request) {
        PropertyDetailResponse property = propertyService.create(currentUser.requireId(), request);
        return ResponseEntity.created(URI.create("/api/v1/properties/" + property.id())).body(property);
    }

    @PutMapping("/properties/{id}")
    @PreAuthorize("hasAnyRole('HOST', 'ADMIN')")
    public PropertyDetailResponse update(@PathVariable UUID id, @Valid @RequestBody PropertyRequest request) {
        return propertyService.update(currentUser.requireId(), isAdmin(), id, request);
    }

    @PatchMapping("/properties/{id}/status")
    @PreAuthorize("hasAnyRole('HOST', 'ADMIN')")
    public PropertyDetailResponse changeStatus(@PathVariable UUID id,
            @Valid @RequestBody PropertyStatusRequest request) {
        return propertyService.changeStatus(currentUser.requireId(), isAdmin(), id, request.status());
    }

    /** Borrado lógico (deleted_at). */
    @DeleteMapping("/properties/{id}")
    @PreAuthorize("hasAnyRole('HOST', 'ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        propertyService.delete(currentUser.requireId(), isAdmin(), id);
    }

    @PutMapping("/properties/{id}/amenities")
    @PreAuthorize("hasAnyRole('HOST', 'ADMIN')")
    public List<AmenityResponse> replaceAmenities(@PathVariable UUID id,
            @Valid @RequestBody PropertyAmenitiesRequest request) {
        return propertyService.replaceAmenities(currentUser.requireId(), isAdmin(), id, request.amenityIds());
    }

    /** Mis alojamientos en cualquier estado (más recientes primero). */
    @GetMapping("/me/properties")
    public PageResponse<PropertySummaryResponse> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return propertyService.mine(currentUser.requireId(), page, size);
    }

    private boolean isAdmin() {
        return currentUser.hasRole(SystemRoles.ADMIN);
    }
}
