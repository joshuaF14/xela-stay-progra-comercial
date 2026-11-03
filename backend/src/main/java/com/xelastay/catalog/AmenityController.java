package com.xelastay.catalog;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.xelastay.catalog.dto.AmenityResponse;
import com.xelastay.catalog.dto.CatalogItemRequest;
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
@RequestMapping("/api/v1/amenities")
public class AmenityController {

    private final AmenityService amenityService;
    private final CurrentUser currentUser;

    public AmenityController(AmenityService amenityService, CurrentUser currentUser) {
        this.amenityService = amenityService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<AmenityResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return amenityService.list(includeInactive && currentUser.hasRole(SystemRoles.ADMIN));
    }

    @GetMapping("/{id}")
    public AmenityResponse get(@PathVariable UUID id) {
        return amenityService.get(id, currentUser.hasRole(SystemRoles.ADMIN));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AmenityResponse> create(@Valid @RequestBody CatalogItemRequest request) {
        AmenityResponse amenity = amenityService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/amenities/" + amenity.id())).body(amenity);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AmenityResponse update(@PathVariable UUID id, @Valid @RequestBody CatalogItemRequest request) {
        return amenityService.update(id, request);
    }

    /** Desactiva (active = false); no borra la fila. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        amenityService.deactivate(id);
    }
}
