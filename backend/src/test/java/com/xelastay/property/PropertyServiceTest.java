package com.xelastay.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.xelastay.audit.AuditService;
import com.xelastay.catalog.Amenity;
import com.xelastay.catalog.AmenityRepository;
import com.xelastay.catalog.Location;
import com.xelastay.catalog.LocationRepository;
import com.xelastay.catalog.LocationService;
import com.xelastay.catalog.PropertyCategory;
import com.xelastay.catalog.PropertyCategoryRepository;
import com.xelastay.catalog.dto.LocationRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

    @Mock
    private PropertyRepository properties;

    @Mock
    private PropertyCategoryRepository categories;

    @Mock
    private LocationRepository locations;

    @Mock
    private LocationService locationService;

    @Mock
    private PropertyImageRepository images;

    @Mock
    private PropertyAmenityRepository propertyAmenities;

    @Mock
    private AmenityRepository amenities;

    @Mock
    private PropertyAssembler assembler;

    @Mock
    private AuditService audit;

    @Captor
    private ArgumentCaptor<Property> saved;

    private final UUID hostId = UUID.randomUUID();
    private final UUID otherUser = UUID.randomUUID();
    private PropertyService service;

    @BeforeEach
    void setUp() {
        service = new PropertyService(properties, categories, locations, locationService, images, propertyAmenities,
                amenities, assembler, audit);
    }

    @Test
    void createIsDraftOwnedByTheTokenUserWithDefaultCurrency() {
        PropertyCategory category = PropertyFixtures.category(true);
        Location location = PropertyFixtures.location();
        when(categories.findById(category.getId())).thenReturn(Optional.of(category));
        when(locations.findById(location.getId())).thenReturn(Optional.of(location));
        when(properties.save(any(Property.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(hostId, PropertyFixtures.request(category.getId(), location.getId(), null));

        verify(properties).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PropertyStatus.DRAFT);
        assertThat(saved.getValue().getHostId()).isEqualTo(hostId);
        assertThat(saved.getValue().getCurrency()).isEqualTo("GTQ");
        assertThat(saved.getValue().getTitle()).isEqualTo("Casa en Xela");
    }

    @Test
    void createCanAlsoCreateTheLocation() {
        PropertyCategory category = PropertyFixtures.category(true);
        LocationRequest newLocation = new LocationRequest("Quetzaltenango", "Salcajá", "Centro", null, null);
        when(categories.findById(category.getId())).thenReturn(Optional.of(category));
        when(locationService.createEntity(newLocation)).thenReturn(PropertyFixtures.location());
        when(properties.save(any(Property.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(hostId, PropertyFixtures.request(category.getId(), null, newLocation));

        verify(locationService).createEntity(newLocation);
        verify(locations, never()).findById(any());
    }

    @Test
    void inactiveCategoryIs400() {
        PropertyCategory category = PropertyFixtures.category(false);
        when(categories.findById(category.getId())).thenReturn(Optional.of(category));

        assertStatus(() -> service.create(hostId, PropertyFixtures.request(category.getId(), UUID.randomUUID(), null)),
                HttpStatus.BAD_REQUEST);
        verify(properties, never()).save(any());
    }

    @Test
    void unpublishedPropertyIsHiddenFromStrangersButNotFromOwnerOrAdmin() {
        Property property = PropertyFixtures.property(hostId, PropertyStatus.DRAFT);
        when(properties.findByIdAndDeletedAtIsNull(property.getId())).thenReturn(Optional.of(property));

        assertStatus(() -> service.get(property.getId(), null, false), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.get(property.getId(), otherUser, false), HttpStatus.NOT_FOUND);
        service.get(property.getId(), hostId, false);
        service.get(property.getId(), otherUser, true);
        verify(assembler, times(2)).detail(property);
    }

    @Test
    void onlyOwnerOrAdminCanEdit() {
        Property property = PropertyFixtures.property(hostId, PropertyStatus.DRAFT);
        when(properties.findByIdAndDeletedAtIsNull(property.getId())).thenReturn(Optional.of(property));

        assertStatus(() -> service.requireEditable(otherUser, false, property.getId()), HttpStatus.FORBIDDEN);
        assertThat(service.requireEditable(otherUser, true, property.getId())).isSameAs(property);
    }

    @Test
    void ownerCannotRejectNorLeaveRejected() {
        Property draft = PropertyFixtures.property(hostId, PropertyStatus.DRAFT);
        Property rejected = PropertyFixtures.property(hostId, PropertyStatus.REJECTED);
        when(properties.findByIdAndDeletedAtIsNull(draft.getId())).thenReturn(Optional.of(draft));
        when(properties.findByIdAndDeletedAtIsNull(rejected.getId())).thenReturn(Optional.of(rejected));

        assertStatus(() -> service.changeStatus(hostId, false, draft.getId(), PropertyStatus.REJECTED),
                HttpStatus.FORBIDDEN);
        assertStatus(() -> service.changeStatus(hostId, false, rejected.getId(), PropertyStatus.DRAFT),
                HttpStatus.CONFLICT);
    }

    @Test
    void publishingRequiresAnImage() {
        Property property = PropertyFixtures.property(hostId, PropertyStatus.DRAFT);
        when(properties.findByIdAndDeletedAtIsNull(property.getId())).thenReturn(Optional.of(property));
        when(images.countByPropertyId(property.getId())).thenReturn(0L, 1L);

        assertStatus(() -> service.changeStatus(hostId, false, property.getId(), PropertyStatus.PUBLISHED),
                HttpStatus.CONFLICT);
        service.changeStatus(hostId, false, property.getId(), PropertyStatus.PUBLISHED);

        assertThat(property.getStatus()).isEqualTo(PropertyStatus.PUBLISHED);
    }

    @Test
    void adminCanReject() {
        Property property = PropertyFixtures.property(hostId, PropertyStatus.PUBLISHED);
        when(properties.findByIdAndDeletedAtIsNull(property.getId())).thenReturn(Optional.of(property));

        service.changeStatus(otherUser, true, property.getId(), PropertyStatus.REJECTED);

        assertThat(property.getStatus()).isEqualTo(PropertyStatus.REJECTED);
        // El dueño es hostId: AuditService decide source = ADMIN porque el actor (ADMIN) no es el dueño.
        verify(audit).recordAction("PROPERTY_STATUS_CHANGED", "property", property.getId(),
                Map.of("status", "PUBLISHED"), Map.of("status", "REJECTED"), hostId);
    }

    @Test
    void deleteWithUpcomingBookingsIs409() {
        Property property = PropertyFixtures.property(hostId, PropertyStatus.PUBLISHED);
        when(properties.findByIdAndDeletedAtIsNull(property.getId())).thenReturn(Optional.of(property));
        when(properties.hasActiveFutureBookings(eq(property.getId()), any())).thenReturn(true);

        assertStatus(() -> service.delete(hostId, false, property.getId()), HttpStatus.CONFLICT);
        assertThat(property.getDeletedAt()).isNull();
    }

    @Test
    void deleteIsLogical() {
        Property property = PropertyFixtures.property(hostId, PropertyStatus.PAUSED);
        when(properties.findByIdAndDeletedAtIsNull(property.getId())).thenReturn(Optional.of(property));
        when(properties.hasActiveFutureBookings(eq(property.getId()), any())).thenReturn(false);

        service.delete(hostId, false, property.getId());

        assertThat(property.getDeletedAt()).isNotNull();
        verify(properties).save(property);
        verify(properties, never()).delete(any(Property.class));
    }

    @Test
    void replaceAmenitiesRejectsUnknownAndNewInactive() {
        Property property = PropertyFixtures.property(hostId, PropertyStatus.DRAFT);
        Amenity inactive = amenity(false);
        when(properties.findByIdAndDeletedAtIsNull(property.getId())).thenReturn(Optional.of(property));
        when(amenities.findAllById(any())).thenReturn(List.of(), List.of(inactive));
        when(propertyAmenities.findAmenityIds(property.getId())).thenReturn(List.of());

        assertStatus(() -> service.replaceAmenities(hostId, false, property.getId(), List.of(UUID.randomUUID())),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.replaceAmenities(hostId, false, property.getId(), List.of(inactive.getId())),
                HttpStatus.BAD_REQUEST);
        verify(propertyAmenities, never()).deleteByPropertyId(any());
    }

    @Test
    void replaceAmenitiesReplacesTheSet() {
        Property property = PropertyFixtures.property(hostId, PropertyStatus.DRAFT);
        Amenity wifi = amenity(true);
        when(properties.findByIdAndDeletedAtIsNull(property.getId())).thenReturn(Optional.of(property));
        when(amenities.findAllById(any())).thenReturn(List.of(wifi));
        when(propertyAmenities.findAmenityIds(property.getId())).thenReturn(List.of());

        assertThat(service.replaceAmenities(hostId, false, property.getId(), List.of(wifi.getId(), wifi.getId())))
                .hasSize(1);
        verify(propertyAmenities).deleteByPropertyId(property.getId());
        verify(propertyAmenities).saveAll(any());
    }

    @Test
    void searchValidatesPriceRangeAndSort() {
        PropertySearch inverted = new PropertySearch(null, null, null, new BigDecimal("500"), new BigDecimal("100"),
                null, null, null);
        assertStatus(() -> service.search(inverted, 0, 20, null), HttpStatus.BAD_REQUEST);
        assertStatus(() -> PropertyService.parseSort("hostId,asc"), HttpStatus.BAD_REQUEST);
        assertThat(PropertyService.parseSort("pricePerNight,asc").getOrderFor("pricePerNight").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(PropertyService.parseSort(null).getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    private static Amenity amenity(boolean active) {
        Amenity amenity = Amenity.create("WiFi", null, active);
        ReflectionTestUtils.setField(amenity, "id", UUID.randomUUID());
        return amenity;
    }

    private static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(status));
    }
}
