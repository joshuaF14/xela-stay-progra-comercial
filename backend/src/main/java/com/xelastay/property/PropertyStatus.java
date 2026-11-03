package com.xelastay.property;

import java.util.Set;

/** Estados de properties.status (CHECK de V1). Solo PUBLISHED es visible al público. */
public enum PropertyStatus {
    DRAFT,
    PUBLISHED,
    PAUSED,
    REJECTED;

    /** Estados que el dueño puede asignar; REJECTED solo lo asigna (y lo levanta) un ADMIN. */
    public static final Set<PropertyStatus> OWNER_MANAGED = Set.of(DRAFT, PUBLISHED, PAUSED);
}
