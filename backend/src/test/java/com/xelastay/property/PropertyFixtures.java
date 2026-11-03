package com.xelastay.property;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.xelastay.catalog.Location;
import com.xelastay.catalog.PropertyCategory;
import com.xelastay.catalog.dto.LocationRequest;
import com.xelastay.catalog.dto.LocationResponse;
import com.xelastay.property.dto.PropertyDetailResponse;
import com.xelastay.property.dto.PropertyRequest;
import com.xelastay.property.dto.PropertySummaryResponse;

import org.springframework.test.util.ReflectionTestUtils;

/** Datos de prueba compartidos por las pruebas de alojamientos. */
final class PropertyFixtures {

    private PropertyFixtures() {
    }

    static PropertyCategory category(boolean active) {
        PropertyCategory category = PropertyCategory.create("Casa", null, active);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        return category;
    }

    static Location location() {
        Location location = Location.create("Quetzaltenango", "Quetzaltenango", "Zona 1",
                new BigDecimal("14.8347000"), new BigDecimal("-91.5180000"));
        ReflectionTestUtils.setField(location, "id", UUID.randomUUID());
        return location;
    }

    static Property property(UUID hostId, PropertyStatus status) {
        Property property = Property.draft(hostId, category(true), location(), "Casa en Xela", "Bonita",
                new BigDecimal("350.00"), "GTQ", 4, 2, new BigDecimal("1.5"));
        property.changeStatus(status);
        ReflectionTestUtils.setField(property, "id", UUID.randomUUID());
        return property;
    }

    static PropertyRequest request(UUID categoryId, UUID locationId, LocationRequest location) {
        return new PropertyRequest(categoryId, locationId, location, " Casa en Xela ", "Bonita",
                new BigDecimal("350.00"), null, 4, 2, new BigDecimal("1.5"));
    }

    static PropertyDetailResponse detail(UUID id, PropertyStatus status) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new PropertyDetailResponse(id, "Casa en Xela", "Bonita", new BigDecimal("350.00"), "GTQ", 4, 2,
                new BigDecimal("1.5"), status, new PropertySummaryResponse.CategoryRef(UUID.randomUUID(), "Casa"),
                locationResponse(), new PropertyDetailResponse.HostRef(UUID.randomUUID(), "Ana"), List.of(), List.of(),
                null, 0, now, now);
    }

    static PropertySummaryResponse summary(UUID id) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new PropertySummaryResponse(id, UUID.randomUUID(), "Casa en Xela", "Bonita", new BigDecimal("350.00"),
                "GTQ", 4, 2, new BigDecimal("1.5"), PropertyStatus.PUBLISHED,
                new PropertySummaryResponse.CategoryRef(UUID.randomUUID(), "Casa"), locationResponse(),
                "https://img.example.com/1.jpg", new BigDecimal("4.50"), 2, now, now);
    }

    private static LocationResponse locationResponse() {
        return new LocationResponse(UUID.randomUUID(), "Quetzaltenango", "Quetzaltenango", "Zona 1",
                new BigDecimal("14.8347000"), new BigDecimal("-91.5180000"), OffsetDateTime.now(ZoneOffset.UTC));
    }
}
