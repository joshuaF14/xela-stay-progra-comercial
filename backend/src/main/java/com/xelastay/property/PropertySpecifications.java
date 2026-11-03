package com.xelastay.property;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

/** Filtros de la búsqueda de alojamientos. */
final class PropertySpecifications {

    private PropertySpecifications() {
    }

    static Specification<Property> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    static Specification<Property> hasStatus(PropertyStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<Property> hostedBy(UUID hostId) {
        return (root, query, cb) -> cb.equal(root.get("hostId"), hostId);
    }

    static Specification<Property> inCategory(UUID categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    /** Igualdad sin distinguir mayúsculas sobre un campo de la ubicación (department o municipality). */
    static Specification<Property> locationEquals(String field, String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(cb.trim(root.get("location").get(field))), normalized);
    }

    static Specification<Property> minPrice(BigDecimal min) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("pricePerNight"), min);
    }

    static Specification<Property> maxPrice(BigDecimal max) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("pricePerNight"), max);
    }

    static Specification<Property> fitsGuests(int guests) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("capacity"), guests);
    }

    /** El alojamiento tiene esa amenidad (se combina una por cada id para exigir todas). */
    static Specification<Property> hasAmenity(UUID amenityId) {
        return (root, query, cb) -> {
            var subquery = query.subquery(Integer.class);
            var assignment = subquery.from(PropertyAmenity.class);
            subquery.select(cb.literal(1)).where(
                    cb.equal(assignment.get("id").get("propertyId"), root.get("id")),
                    cb.equal(assignment.get("id").get("amenityId"), amenityId));
            return cb.exists(subquery);
        };
    }

    /** Texto en el título o la descripción, sin distinguir mayúsculas. */
    static Specification<Property> matchesText(String text) {
        String pattern = "%" + text.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern, '\\'),
                cb.like(cb.lower(root.get("description")), pattern, '\\'));
    }
}
