package com.xelastay.property;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.xelastay.audit.AuditService;
import com.xelastay.property.dto.PropertyImageRequest;
import com.xelastay.property.dto.PropertyImageResponse;
import com.xelastay.property.dto.PropertyImageUpdateRequest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Imágenes de un alojamiento (dueño o ADMIN). Una sola portada: antes de marcar otra se quita la actual, para
 * respetar el índice único de V1. La primera imagen se vuelve portada; si se borra la portada, pasa a la siguiente.
 */
@Service
public class PropertyImageService {

    private final PropertyService propertyService;
    private final PropertyImageRepository images;
    private final AuditService audit;

    public PropertyImageService(PropertyService propertyService, PropertyImageRepository images,
            AuditService audit) {
        this.propertyService = propertyService;
        this.images = images;
        this.audit = audit;
    }

    @Transactional
    public PropertyImageResponse add(UUID userId, boolean admin, UUID propertyId, PropertyImageRequest request) {
        Property property = propertyService.requireEditable(userId, admin, propertyId);
        boolean first = images.countByPropertyId(propertyId) == 0;
        boolean cover = first || Boolean.TRUE.equals(request.cover());
        if (cover && !first) {
            images.clearCover(propertyId);
        }
        int order = request.displayOrder() != null ? request.displayOrder() : images.maxDisplayOrder(propertyId) + 1;
        PropertyImage image = PropertyImage.create(propertyId, request.imageUrl().trim(),
                blankToNull(request.caption()), order, cover);
        PropertyImageResponse created = PropertyAssembler.image(images.save(image));
        audit.recordAction("PROPERTY_IMAGE_ADDED", "property_image", created.id(), null, withProperty(created,
                propertyId), property.getHostId());
        return created;
    }

    @Transactional
    public PropertyImageResponse update(UUID userId, boolean admin, UUID propertyId, UUID imageId,
            PropertyImageUpdateRequest request) {
        Property property = propertyService.requireEditable(userId, admin, propertyId);
        PropertyImage image = find(propertyId, imageId);
        Map<String, Object> before = withProperty(PropertyAssembler.image(image), propertyId);
        if (request.caption() != null) {
            image.changeCaption(blankToNull(request.caption()));
        }
        if (request.displayOrder() != null) {
            image.changeDisplayOrder(request.displayOrder());
        }
        if (Boolean.TRUE.equals(request.cover()) && !image.isCover()) {
            images.clearCover(propertyId);
            image.changeCover(true);
        } else if (Boolean.FALSE.equals(request.cover())) {
            image.changeCover(false);
        }
        PropertyImageResponse updated = PropertyAssembler.image(images.save(image));
        audit.recordAction("PROPERTY_IMAGE_UPDATED", "property_image", imageId, before,
                withProperty(updated, propertyId), property.getHostId());
        return updated;
    }

    /** No se puede dejar sin imágenes un alojamiento publicado (409). */
    @Transactional
    public void delete(UUID userId, boolean admin, UUID propertyId, UUID imageId) {
        Property property = propertyService.requireEditable(userId, admin, propertyId);
        PropertyImage image = find(propertyId, imageId);
        if (property.getStatus() == PropertyStatus.PUBLISHED && images.countByPropertyId(propertyId) == 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un alojamiento publicado debe conservar al menos una imagen");
        }
        images.delete(image);
        images.flush();
        audit.recordAction("PROPERTY_IMAGE_DELETED", "property_image", imageId,
                withProperty(PropertyAssembler.image(image), propertyId), null, property.getHostId());
        if (image.isCover()) {
            List<PropertyImage> remaining = images.findByPropertyIdOrderByDisplayOrderAscCreatedAtAsc(propertyId);
            if (!remaining.isEmpty()) {
                remaining.get(0).changeCover(true);
                images.save(remaining.get(0));
            }
        }
    }

    private PropertyImage find(UUID propertyId, UUID imageId) {
        return images.findByIdAndPropertyId(imageId, propertyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagen no encontrada"));
    }

    /** Datos de la imagen más el alojamiento al que pertenece, para auditoría. */
    private static Map<String, Object> withProperty(PropertyImageResponse image, UUID propertyId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("propertyId", propertyId);
        values.put("imageUrl", image.imageUrl());
        values.put("caption", image.caption());
        values.put("displayOrder", image.displayOrder());
        values.put("cover", image.cover());
        return values;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
