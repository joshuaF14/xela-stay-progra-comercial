package com.xelastay.audit;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Registro de auditoría (tabla audit_logs de V1). Solo se inserta; nunca se modifica ni se borra. */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "action", nullable = false, updatable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> newValues;

    // INET: se envía como texto y PostgreSQL lo convierte.
    @ColumnTransformer(write = "CAST(? AS inet)")
    @Column(name = "ip_address", updatable = false, columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "user_agent", updatable = false, columnDefinition = "text")
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false, length = 30)
    private AuditSource source;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    protected AuditLog() {
    }

    public static AuditLog of(UUID actorId, String action, String entityType, UUID entityId,
            Map<String, Object> oldValues, Map<String, Object> newValues, AuditSource source, String ipAddress,
            String userAgent) {
        AuditLog log = new AuditLog();
        log.actorId = actorId;
        log.action = action;
        log.entityType = entityType;
        log.entityId = entityId;
        log.oldValues = oldValues;
        log.newValues = newValues;
        log.source = source;
        log.ipAddress = ipAddress;
        log.userAgent = userAgent;
        log.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        return log;
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public Map<String, Object> getOldValues() {
        return oldValues;
    }

    public Map<String, Object> getNewValues() {
        return newValues;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public AuditSource getSource() {
        return source;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
