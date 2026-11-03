package com.xelastay.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    static Specification<AuditLog> fieldEquals(String field, Object value) {
        return (root, query, cb) -> cb.equal(root.get(field), value);
    }

    static Specification<AuditLog> createdFrom(OffsetDateTime from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    static Specification<AuditLog> createdBefore(OffsetDateTime before) {
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), before);
    }
}
