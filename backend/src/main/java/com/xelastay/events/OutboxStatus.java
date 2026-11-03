package com.xelastay.events;

/** Estados de outbox_events.status (CHECK de V1). PROCESSING existe en V1 pero no se usa: el bloqueo es con SKIP LOCKED. */
public enum OutboxStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED
}
