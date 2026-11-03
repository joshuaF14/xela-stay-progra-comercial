package com.xelastay.audit;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xelastay.audit.dto.AuditLogResponse;
import com.xelastay.common.PageResponse;
import com.xelastay.common.Paging;
import com.xelastay.common.ProfileNameReader;
import com.xelastay.profile.SystemRoles;
import com.xelastay.security.CurrentUser;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * Escribe y consulta audit_logs. Cada registro se une a la transacción en curso: si la operación auditada se
 * revierte, su registro también. La IP y el user agent salen de la petición HTTP actual, si existe.
 */
@Service
public class AuditService {

    static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");
    private static final int MAX_USER_AGENT = 500;
    /** Solo se guarda algo con forma de IPv4/IPv6; cualquier otro valor iría a parar a un CAST a inet inválido. */
    private static final Pattern IP = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");
    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() { };

    private final AuditLogRepository logs;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;
    private final ProfileNameReader profileNames;

    public AuditService(AuditLogRepository logs, CurrentUser currentUser, ObjectMapper objectMapper,
            ProfileNameReader profileNames) {
        this.logs = logs;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
        this.profileNames = profileNames;
    }

    /**
     * Registra una operación con IP y user agent de la petición actual.
     *
     * @param oldValues estado anterior (Map, record o null)
     * @param newValues estado nuevo (Map, record o null)
     */
    @Transactional
    public void record(UUID actorId, String action, String entityType, UUID entityId, Object oldValues,
            Object newValues, AuditSource source) {
        HttpServletRequest request = currentRequest();
        record(actorId, action, entityType, entityId, oldValues, newValues, source,
                request == null ? null : clientIp(request),
                request == null ? null : request.getHeader(HttpHeaders.USER_AGENT));
    }

    /** Igual que {@link #record(UUID, String, String, UUID, Object, Object, AuditSource)} con IP y user agent dados. */
    @Transactional
    public void record(UUID actorId, String action, String entityType, UUID entityId, Object oldValues,
            Object newValues, AuditSource source, String ipAddress, String userAgent) {
        logs.save(AuditLog.of(actorId, action, entityType, entityId, toMap(oldValues), toMap(newValues), source,
                validIp(ipAddress), truncate(userAgent)));
    }

    /**
     * Acción del usuario del token. {@code source = ADMIN} si es ADMIN y el recurso no es suyo
     * ({@code ownerId} distinto de él, o null para recursos compartidos como el catálogo); USER en los demás casos.
     */
    @Transactional
    public void recordAction(String action, String entityType, UUID entityId, Object oldValues, Object newValues,
            UUID ownerId) {
        UUID actorId = currentUser.id().orElse(null);
        boolean onSomethingElse = actorId == null || !actorId.equals(ownerId);
        AuditSource source = currentUser.hasRole(SystemRoles.ADMIN) && onSomethingElse
                ? AuditSource.ADMIN
                : AuditSource.USER;
        record(actorId, action, entityType, entityId, oldValues, newValues, source);
    }

    /** Acción del usuario del token sobre algo en lo que participa (por ejemplo, una reserva): source = USER. */
    @Transactional
    public void recordUserAction(String action, String entityType, UUID entityId, Object oldValues,
            Object newValues) {
        record(currentUser.id().orElse(null), action, entityType, entityId, oldValues, newValues, AuditSource.USER);
    }

    /** ADMIN: más recientes primero; filtros opcionales y días [from, to] en hora de Guatemala (incluidos). */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(String entityType, UUID entityId, UUID actorId, AuditSource source,
            LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from no puede ser posterior a to");
        }
        List<Specification<AuditLog>> filters = new ArrayList<>();
        if (entityType != null && !entityType.isBlank()) {
            filters.add(AuditLogRepository.fieldEquals("entityType", entityType.trim()));
        }
        if (entityId != null) {
            filters.add(AuditLogRepository.fieldEquals("entityId", entityId));
        }
        if (actorId != null) {
            filters.add(AuditLogRepository.fieldEquals("actorId", actorId));
        }
        if (source != null) {
            filters.add(AuditLogRepository.fieldEquals("source", source));
        }
        if (from != null) {
            filters.add(AuditLogRepository.createdFrom(startOfDay(from)));
        }
        if (to != null) {
            filters.add(AuditLogRepository.createdBefore(startOfDay(to.plusDays(1))));
        }
        Page<AuditLog> result = logs.findAll(Specification.allOf(filters),
                Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<UUID, String> names = profileNames.findNames(result.getContent().stream()
                .map(AuditLog::getActorId).filter(id -> id != null).distinct().toList());
        return PageResponse.of(result, result.getContent().stream().map(log -> new AuditLogResponse(log.getId(),
                log.getActorId(), log.getActorId() == null ? null : names.get(log.getActorId()), log.getAction(),
                log.getEntityType(), log.getEntityId(), log.getOldValues(), log.getNewValues(), log.getIpAddress(),
                log.getUserAgent(), log.getSource(), log.getCreatedAt())).toList());
    }

    private Map<String, Object> toMap(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, MAP);
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }

    /** Primera IP de X-Forwarded-For (detrás de un proxy) o la dirección remota. */
    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    static String validIp(String ip) {
        return ip != null && IP.matcher(ip.trim()).matches() ? ip.trim() : null;
    }

    private static String truncate(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return userAgent.length() > MAX_USER_AGENT ? userAgent.substring(0, MAX_USER_AGENT) : userAgent;
    }

    private static OffsetDateTime startOfDay(LocalDate day) {
        return day.atStartOfDay(GUATEMALA).toOffsetDateTime();
    }
}
