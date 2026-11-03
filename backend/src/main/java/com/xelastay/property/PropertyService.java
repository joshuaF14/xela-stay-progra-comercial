package com.xelastay.property;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.xelastay.audit.AuditService;
import com.xelastay.catalog.Amenity;
import com.xelastay.catalog.AmenityRepository;
import com.xelastay.catalog.AmenityService;
import com.xelastay.catalog.Location;
import com.xelastay.catalog.LocationRepository;
import com.xelastay.catalog.LocationService;
import com.xelastay.catalog.PropertyCategory;
import com.xelastay.catalog.PropertyCategoryRepository;
import com.xelastay.catalog.dto.AmenityResponse;
import com.xelastay.common.PageResponse;
import com.xelastay.common.Paging;
import com.xelastay.property.dto.PropertyDetailResponse;
import com.xelastay.property.dto.PropertyRequest;
import com.xelastay.property.dto.PropertySummaryResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Alojamientos: búsqueda pública, detalle y administración por su anfitrión (o un ADMIN). */
@Service
public class PropertyService {

    static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");
    static final String DEFAULT_CURRENCY = "GTQ";
    private static final Set<String> SORTABLE = Set.of("pricePerNight", "createdAt", "title");

    private final PropertyRepository properties;
    private final PropertyCategoryRepository categories;
    private final LocationRepository locations;
    private final LocationService locationService;
    private final PropertyImageRepository images;
    private final PropertyAmenityRepository propertyAmenities;
    private final AmenityRepository amenities;
    private final PropertyAssembler assembler;
    private final AuditService audit;

    public PropertyService(PropertyRepository properties, PropertyCategoryRepository categories,
            LocationRepository locations, LocationService locationService, PropertyImageRepository images,
            PropertyAmenityRepository propertyAmenities, AmenityRepository amenities, PropertyAssembler assembler,
            AuditService audit) {
        this.properties = properties;
        this.categories = categories;
        this.locations = locations;
        this.locationService = locationService;
        this.images = images;
        this.propertyAmenities = propertyAmenities;
        this.amenities = amenities;
        this.assembler = assembler;
        this.audit = audit;
    }

    /** Solo PUBLISHED y no borrados. Orden por defecto: más recientes primero. */
    @Transactional(readOnly = true)
    public PageResponse<PropertySummaryResponse> search(PropertySearch filter, int page, int size, String sort) {
        if (filter.minPrice() != null && filter.maxPrice() != null
                && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
            throw error(HttpStatus.BAD_REQUEST, "minPrice no puede ser mayor que maxPrice");
        }
        List<Specification<Property>> specs = new ArrayList<>();
        specs.add(PropertySpecifications.notDeleted());
        specs.add(PropertySpecifications.hasStatus(PropertyStatus.PUBLISHED));
        if (hasText(filter.department())) {
            specs.add(PropertySpecifications.locationEquals("department", filter.department()));
        }
        if (hasText(filter.municipality())) {
            specs.add(PropertySpecifications.locationEquals("municipality", filter.municipality()));
        }
        if (filter.categoryId() != null) {
            specs.add(PropertySpecifications.inCategory(filter.categoryId()));
        }
        if (filter.minPrice() != null) {
            specs.add(PropertySpecifications.minPrice(filter.minPrice()));
        }
        if (filter.maxPrice() != null) {
            specs.add(PropertySpecifications.maxPrice(filter.maxPrice()));
        }
        if (filter.guests() != null && filter.guests() > 0) {
            specs.add(PropertySpecifications.fitsGuests(filter.guests()));
        }
        if (filter.amenityIds() != null) {
            new LinkedHashSet<>(filter.amenityIds()).forEach(id -> specs.add(PropertySpecifications.hasAmenity(id)));
        }
        if (hasText(filter.q())) {
            specs.add(PropertySpecifications.matchesText(filter.q()));
        }
        return page(Specification.allOf(specs), page, size, parseSort(sort));
    }

    /** Alojamientos del anfitrión en cualquier estado (no borrados). */
    @Transactional(readOnly = true)
    public PageResponse<PropertySummaryResponse> mine(UUID hostId, int page, int size) {
        return page(Specification.allOf(PropertySpecifications.notDeleted(), PropertySpecifications.hostedBy(hostId)),
                page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /** Público si está PUBLISHED; el dueño y ADMIN lo ven en cualquier estado. Para el resto, 404. */
    @Transactional(readOnly = true)
    public PropertyDetailResponse get(UUID id, UUID viewerId, boolean admin) {
        Property property = findActive(id);
        boolean owner = viewerId != null && property.isOwnedBy(viewerId);
        if (property.getStatus() != PropertyStatus.PUBLISHED && !owner && !admin) {
            throw notFound();
        }
        return assembler.detail(property);
    }

    /** Nace en DRAFT con el usuario del token como anfitrión. */
    @Transactional
    public PropertyDetailResponse create(UUID hostId, PropertyRequest request) {
        Property property = Property.draft(hostId, category(request.categoryId(), null), location(request),
                request.title().trim(), blankToNull(request.description()), request.pricePerNight(),
                currency(request.currency()), request.capacity(), request.bedrooms(), request.bathrooms());
        Property saved = properties.save(property);
        audit.recordAction("PROPERTY_CREATED", "property", saved.getId(), null, snapshot(saved), hostId);
        return assembler.detail(saved);
    }

    @Transactional
    public PropertyDetailResponse update(UUID userId, boolean admin, UUID id, PropertyRequest request) {
        Property property = requireEditable(userId, admin, id);
        Map<String, Object> before = snapshot(property);
        property.update(category(request.categoryId(), property.getCategory()), location(request),
                request.title().trim(), blankToNull(request.description()), request.pricePerNight(),
                currency(request.currency()), request.capacity(), request.bedrooms(), request.bathrooms());
        Property saved = properties.save(property);
        audit.recordAction("PROPERTY_UPDATED", "property", id, before, snapshot(saved), property.getHostId());
        return assembler.detail(saved);
    }

    /**
     * El dueño mueve entre DRAFT, PUBLISHED y PAUSED; REJECTED lo asigna y lo levanta solo un ADMIN.
     * Para publicar se necesita al menos una imagen.
     */
    @Transactional
    public PropertyDetailResponse changeStatus(UUID userId, boolean admin, UUID id, PropertyStatus target) {
        Property property = requireEditable(userId, admin, id);
        if (property.getStatus() == target) {
            return assembler.detail(property);
        }
        if (!admin && !PropertyStatus.OWNER_MANAGED.contains(target)) {
            throw error(HttpStatus.FORBIDDEN, "Solo un administrador puede rechazar un alojamiento");
        }
        if (!admin && property.getStatus() == PropertyStatus.REJECTED) {
            throw error(HttpStatus.CONFLICT, "El alojamiento fue rechazado; solo un administrador puede cambiar su estado");
        }
        if (target == PropertyStatus.PUBLISHED && images.countByPropertyId(id) == 0) {
            throw error(HttpStatus.CONFLICT, "Para publicar, el alojamiento necesita al menos una imagen");
        }
        PropertyStatus previous = property.getStatus();
        property.changeStatus(target);
        Property saved = properties.save(property);
        audit.recordAction("PROPERTY_STATUS_CHANGED", "property", id, Map.of("status", previous.name()),
                Map.of("status", target.name()), property.getHostId());
        return assembler.detail(saved);
    }

    /** Borrado lógico. No se permite con reservas PENDING o CONFIRMED que aún no terminan. */
    @Transactional
    public void delete(UUID userId, boolean admin, UUID id) {
        Property property = requireEditable(userId, admin, id);
        if (properties.hasActiveFutureBookings(id, LocalDate.now(GUATEMALA))) {
            throw error(HttpStatus.CONFLICT, "El alojamiento tiene reservas pendientes o confirmadas por venir");
        }
        property.markDeleted();
        properties.save(property);
        audit.recordAction("PROPERTY_DELETED", "property", id, snapshot(property),
                Map.of("deletedAt", property.getDeletedAt().toString()), property.getHostId());
    }

    /** Reemplaza las amenidades. Una amenidad inactiva solo se acepta si el alojamiento ya la tenía. */
    @Transactional
    public List<AmenityResponse> replaceAmenities(UUID userId, boolean admin, UUID id, List<UUID> amenityIds) {
        Property property = requireEditable(userId, admin, id);
        Set<UUID> wanted = new LinkedHashSet<>(amenityIds);
        Map<UUID, Amenity> found = amenities.findAllById(wanted).stream()
                .collect(Collectors.toMap(Amenity::getId, Function.identity()));
        if (found.size() != wanted.size()) {
            throw error(HttpStatus.BAD_REQUEST, "Una o más amenidades no existen");
        }
        Set<UUID> current = new LinkedHashSet<>(propertyAmenities.findAmenityIds(id));
        boolean addsInactive = found.values().stream().anyMatch(a -> !a.isActive() && !current.contains(a.getId()));
        if (addsInactive) {
            throw error(HttpStatus.BAD_REQUEST, "No se pueden agregar amenidades inactivas");
        }
        propertyAmenities.deleteByPropertyId(id);
        propertyAmenities.saveAll(wanted.stream().map(amenityId -> PropertyAmenity.of(id, amenityId)).toList());
        audit.recordAction("PROPERTY_AMENITIES_REPLACED", "property", id, Map.of("amenityIds", List.copyOf(current)),
                Map.of("amenityIds", List.copyOf(wanted)), property.getHostId());
        return found.values().stream().map(AmenityService::toResponse)
                .sorted(Comparator.comparing(AmenityResponse::name)).toList();
    }

    /** Alojamiento no borrado que el usuario puede modificar: su dueño o un ADMIN (403 para el resto). */
    @Transactional(readOnly = true)
    public Property requireEditable(UUID userId, boolean admin, UUID id) {
        Property property = findActive(id);
        if (!admin && !property.isOwnedBy(userId)) {
            throw error(HttpStatus.FORBIDDEN, "Solo el anfitrión del alojamiento puede modificarlo");
        }
        return property;
    }

    private PageResponse<PropertySummaryResponse> page(Specification<Property> spec, int page, int size, Sort sort) {
        Page<Property> result = properties.findAll(spec, Paging.of(page, size, sort));
        return PageResponse.of(result, assembler.summaries(result.getContent()));
    }

    private Property findActive(UUID id) {
        return properties.findByIdAndDeletedAtIsNull(id).orElseThrow(PropertyService::notFound);
    }

    /** La categoría debe existir y estar activa, salvo que sea la que el alojamiento ya tiene. */
    private PropertyCategory category(UUID categoryId, PropertyCategory current) {
        if (current != null && current.getId().equals(categoryId)) {
            return current;
        }
        return categories.findById(categoryId).filter(PropertyCategory::isActive)
                .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "La categoría no existe o está inactiva"));
    }

    private Location location(PropertyRequest request) {
        if (request.locationId() != null) {
            return locations.findById(request.locationId())
                    .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "La ubicación no existe"));
        }
        return locationService.createEntity(request.location());
    }

    /** Campos de negocio del alojamiento, para auditoría. */
    static Map<String, Object> snapshot(Property property) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", property.getTitle());
        values.put("description", property.getDescription());
        values.put("categoryId", property.getCategory().getId());
        values.put("locationId", property.getLocation().getId());
        values.put("pricePerNight", property.getPricePerNight());
        values.put("currency", property.getCurrency());
        values.put("capacity", property.getCapacity());
        values.put("bedrooms", property.getBedrooms());
        values.put("bathrooms", property.getBathrooms());
        values.put("status", property.getStatus().name());
        return values;
    }

    /** "campo" o "campo,asc|desc" con campo en pricePerNight, createdAt o title. */
    static Sort parseSort(String sort) {
        if (!hasText(sort)) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!SORTABLE.contains(field)) {
            throw error(HttpStatus.BAD_REQUEST, "sort admite pricePerNight, createdAt o title");
        }
        Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc")
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, field);
    }

    private static String currency(String value) {
        return hasText(value) ? value.trim() : DEFAULT_CURRENCY;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static ResponseStatusException notFound() {
        return error(HttpStatus.NOT_FOUND, "Alojamiento no encontrado");
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
