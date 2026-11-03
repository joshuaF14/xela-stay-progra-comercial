package com.xelastay.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.xelastay.audit.AuditService;
import com.xelastay.catalog.dto.LocationRequest;
import com.xelastay.catalog.dto.LocationResponse;
import com.xelastay.common.PageResponse;
import com.xelastay.common.Paging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ubicaciones. Lectura pública; crean y editan HOST o ADMIN. locations no tiene dueño en V1, así que un HOST
 * solo puede editar una ubicación que no usen alojamientos de otros anfitriones.
 */
@Service
public class LocationService {

    private static final int COORDINATE_SCALE = 7;

    private final LocationRepository locations;
    private final AuditService audit;

    public LocationService(LocationRepository locations, AuditService audit) {
        this.locations = locations;
        this.audit = audit;
    }

    /** Orden: departamento, municipio y dirección. Filtros opcionales sin distinguir mayúsculas. */
    @Transactional(readOnly = true)
    public PageResponse<LocationResponse> search(String department, String municipality, int page, int size) {
        List<Specification<Location>> filters = new ArrayList<>();
        if (department != null && !department.isBlank()) {
            filters.add(LocationRepository.fieldEquals("department", department));
        }
        if (municipality != null && !municipality.isBlank()) {
            filters.add(LocationRepository.fieldEquals("municipality", municipality));
        }
        Page<Location> result = locations.findAll(Specification.allOf(filters),
                Paging.of(page, size, Sort.by("department", "municipality", "address")));
        return PageResponse.of(result, result.getContent().stream().map(LocationService::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public LocationResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional
    public LocationResponse create(LocationRequest request) {
        return toResponse(createEntity(request));
    }

    /** Crea la ubicación y devuelve la entidad (lo usa el módulo de alojamientos en la misma transacción). */
    @Transactional
    public Location createEntity(LocationRequest request) {
        Location location = locations.save(Location.create(request.department().trim(),
                request.municipality().trim(), request.address().trim(), scale(request.latitude()),
                scale(request.longitude())));
        audit.recordAction("LOCATION_CREATED", "location", location.getId(), null, toResponse(location), null);
        return location;
    }

    @Transactional
    public LocationResponse update(UUID userId, boolean admin, UUID id, LocationRequest request) {
        Location location = find(id);
        if (!admin && locations.isUsedByAnotherHost(id, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La ubicación la usan alojamientos de otro anfitrión");
        }
        LocationResponse before = toResponse(location);
        location.update(request.department().trim(), request.municipality().trim(), request.address().trim(),
                scale(request.latitude()), scale(request.longitude()));
        LocationResponse after = toResponse(locations.save(location));
        audit.recordAction("LOCATION_UPDATED", "location", id, before, after, null);
        return after;
    }

    private Location find(UUID id) {
        return locations.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ubicación no encontrada"));
    }

    private static BigDecimal scale(BigDecimal coordinate) {
        return coordinate == null ? null : coordinate.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
    }

    public static LocationResponse toResponse(Location location) {
        return new LocationResponse(location.getId(), location.getDepartment(), location.getMunicipality(),
                location.getAddress(), location.getLatitude(), location.getLongitude(), location.getCreatedAt());
    }
}
