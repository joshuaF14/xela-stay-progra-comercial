package com.xelastay.catalog;

import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationRepository extends JpaRepository<Location, UUID>, JpaSpecificationExecutor<Location> {

    /** Igualdad sin distinguir mayúsculas ni espacios extremos. */
    static Specification<Location> fieldEquals(String field, String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(cb.trim(root.get(field))), normalized);
    }

    /** true si algún alojamiento de otro anfitrión usa esta ubicación. */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM properties WHERE location_id = :locationId AND host_id <> :hostId)",
            nativeQuery = true)
    boolean isUsedByAnotherHost(@Param("locationId") UUID locationId, @Param("hostId") UUID hostId);
}
