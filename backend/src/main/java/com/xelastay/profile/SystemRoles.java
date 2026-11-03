package com.xelastay.profile;

import java.util.Set;

/** Roles del sistema creados en V2. No se pueden renombrar ni borrar. */
public final class SystemRoles {

    public static final String GUEST = "GUEST";
    public static final String HOST = "HOST";
    public static final String ADMIN = "ADMIN";

    public static final Set<String> ALL = Set.of(GUEST, HOST, ADMIN);

    private SystemRoles() {
    }

    public static boolean isSystem(String name) {
        return ALL.contains(name);
    }
}
