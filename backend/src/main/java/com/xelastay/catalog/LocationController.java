package com.xelastay.catalog;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import com.xelastay.catalog.dto.LocationRequest;
import com.xelastay.catalog.dto.LocationResponse;
import com.xelastay.common.PageResponse;
import com.xelastay.profile.SystemRoles;
import com.xelastay.security.CurrentUser;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Listar y ver: públicos. Crear y editar: HOST o ADMIN. No se borran (los alojamientos las referencian). */
@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationService locationService;
    private final CurrentUser currentUser;

    public LocationController(LocationService locationService, CurrentUser currentUser) {
        this.locationService = locationService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<LocationResponse> search(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String municipality,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return locationService.search(department, municipality, page, size);
    }

    @GetMapping("/{id}")
    public LocationResponse get(@PathVariable UUID id) {
        return locationService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HOST', 'ADMIN')")
    public ResponseEntity<LocationResponse> create(@Valid @RequestBody LocationRequest request) {
        LocationResponse location = locationService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/locations/" + location.id())).body(location);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOST', 'ADMIN')")
    public LocationResponse update(@PathVariable UUID id, @Valid @RequestBody LocationRequest request) {
        return locationService.update(currentUser.requireId(), currentUser.hasRole(SystemRoles.ADMIN), id, request);
    }
}
