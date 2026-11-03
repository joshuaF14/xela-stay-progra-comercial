package com.xelastay.catalog;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.xelastay.audit.AuditService;
import com.xelastay.catalog.dto.AmenityResponse;
import com.xelastay.catalog.dto.CatalogItemRequest;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Amenidades. Lectura pública de las activas; alta, edición y desactivación solo ADMIN. */
@Service
public class AmenityService {

    private final AmenityRepository amenities;
    private final AuditService audit;

    public AmenityService(AmenityRepository amenities, AuditService audit) {
        this.amenities = amenities;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AmenityResponse> list(boolean includeInactive) {
        List<Amenity> rows = includeInactive
                ? amenities.findAllByOrderByNameAsc()
                : amenities.findByActiveTrueOrderByNameAsc();
        return rows.stream().map(AmenityService::toResponse).toList();
    }

    /** Una amenidad inactiva solo es visible para quien puede administrarla (404 para el resto). */
    @Transactional(readOnly = true)
    public AmenityResponse get(UUID id, boolean includeInactive) {
        Amenity amenity = find(id);
        if (!amenity.isActive() && !includeInactive) {
            throw notFound();
        }
        return toResponse(amenity);
    }

    @Transactional
    public AmenityResponse create(CatalogItemRequest request) {
        String name = request.name().trim();
        if (amenities.existsByNameIgnoreCase(name)) {
            throw duplicate();
        }
        boolean active = request.active() == null || request.active();
        AmenityResponse created = toResponse(save(Amenity.create(name, blankToNull(request.description()), active)));
        audit.recordAction("AMENITY_CREATED", "amenity", created.id(), null, created, null);
        return created;
    }

    @Transactional
    public AmenityResponse update(UUID id, CatalogItemRequest request) {
        Amenity amenity = find(id);
        String name = request.name().trim();
        if (amenities.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw duplicate();
        }
        boolean active = request.active() == null ? amenity.isActive() : request.active();
        AmenityResponse before = toResponse(amenity);
        amenity.update(name, blankToNull(request.description()), active);
        AmenityResponse after = toResponse(save(amenity));
        audit.recordAction("AMENITY_UPDATED", "amenity", id, before, after, null);
        return after;
    }

    /** Desactiva sin borrar: los alojamientos que ya la tienen la conservan. */
    @Transactional
    public void deactivate(UUID id) {
        Amenity amenity = find(id);
        boolean wasActive = amenity.isActive();
        amenity.deactivate();
        amenities.save(amenity);
        audit.recordAction("AMENITY_DEACTIVATED", "amenity", id, Map.of("active", wasActive), Map.of("active", false),
                null);
    }

    private Amenity save(Amenity amenity) {
        try {
            return amenities.saveAndFlush(amenity);
        } catch (DataIntegrityViolationException ex) {
            throw duplicate();
        }
    }

    private Amenity find(UUID id) {
        return amenities.findById(id).orElseThrow(AmenityService::notFound);
    }

    public static AmenityResponse toResponse(Amenity amenity) {
        return new AmenityResponse(amenity.getId(), amenity.getName(), amenity.getDescription(), amenity.isActive());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Amenidad no encontrada");
    }

    private static ResponseStatusException duplicate() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una amenidad con ese nombre");
    }
}
