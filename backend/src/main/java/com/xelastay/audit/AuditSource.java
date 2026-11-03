package com.xelastay.audit;

/** Origen de un registro de auditoría (CHECK de audit_logs.source en V1). */
public enum AuditSource {
    SYSTEM,
    USER,
    AI,
    ADMIN
}
