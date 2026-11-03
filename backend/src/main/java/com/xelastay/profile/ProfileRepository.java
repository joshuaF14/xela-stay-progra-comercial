package com.xelastay.profile;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProfileRepository extends JpaRepository<Profile, UUID>, JpaSpecificationExecutor<Profile> {

    static Specification<Profile> hasStatus(ProfileStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** Perfiles que tienen asignado el rol con ese nombre. */
    static Specification<Profile> hasRole(String roleName) {
        return (root, query, cb) -> {
            var subquery = query.subquery(Integer.class);
            var assignment = subquery.from(ProfileRole.class);
            subquery.select(cb.literal(1)).where(
                    cb.equal(assignment.get("id").get("profileId"), root.get("id")),
                    cb.equal(assignment.get("role").get("name"), roleName));
            return cb.exists(subquery);
        };
    }
}
