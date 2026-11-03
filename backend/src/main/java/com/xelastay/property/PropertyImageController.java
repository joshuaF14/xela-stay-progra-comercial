package com.xelastay.property;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import com.xelastay.profile.SystemRoles;
import com.xelastay.property.dto.PropertyImageRequest;
import com.xelastay.property.dto.PropertyImageResponse;
import com.xelastay.property.dto.PropertyImageUpdateRequest;
import com.xelastay.security.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Imágenes de un alojamiento: anfitrión del alojamiento o ADMIN. Se leen en el detalle del alojamiento. */
@RestController
@RequestMapping("/api/v1/properties/{propertyId}/images")
@PreAuthorize("hasAnyRole('HOST', 'ADMIN')")
public class PropertyImageController {

    private final PropertyImageService imageService;
    private final CurrentUser currentUser;

    public PropertyImageController(PropertyImageService imageService, CurrentUser currentUser) {
        this.imageService = imageService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<PropertyImageResponse> add(@PathVariable UUID propertyId,
            @Valid @RequestBody PropertyImageRequest request) {
        PropertyImageResponse image = imageService.add(currentUser.requireId(), isAdmin(), propertyId, request);
        return ResponseEntity.created(URI.create("/api/v1/properties/" + propertyId + "/images/" + image.id()))
                .body(image);
    }

    @PatchMapping("/{imageId}")
    public PropertyImageResponse update(@PathVariable UUID propertyId, @PathVariable UUID imageId,
            @Valid @RequestBody PropertyImageUpdateRequest request) {
        return imageService.update(currentUser.requireId(), isAdmin(), propertyId, imageId, request);
    }

    @DeleteMapping("/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID propertyId, @PathVariable UUID imageId) {
        imageService.delete(currentUser.requireId(), isAdmin(), propertyId, imageId);
    }

    private boolean isAdmin() {
        return currentUser.hasRole(SystemRoles.ADMIN);
    }
}
