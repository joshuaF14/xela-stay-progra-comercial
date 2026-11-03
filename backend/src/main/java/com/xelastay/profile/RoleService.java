package com.xelastay.profile;

import java.util.List;
import java.util.UUID;

import com.xelastay.profile.dto.RoleRequest;
import com.xelastay.profile.dto.RoleResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** CRUD de roles (solo ADMIN). GUEST, HOST y ADMIN no se renombran ni se borran. */
@Service
public class RoleService {

    private final RoleRepository roles;
    private final ProfileRoleRepository profileRoles;

    public RoleService(RoleRepository roles, ProfileRoleRepository profileRoles) {
        this.roles = roles;
        this.profileRoles = profileRoles;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roles.findAllByOrderByNameAsc().stream().map(RoleService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional
    public RoleResponse create(RoleRequest request) {
        if (roles.existsByName(request.name())) {
            throw error(HttpStatus.CONFLICT, "Ya existe un rol con ese nombre");
        }
        return toResponse(roles.save(Role.create(request.name(), blankToNull(request.description()))));
    }

    @Transactional
    public RoleResponse update(UUID id, RoleRequest request) {
        Role role = find(id);
        boolean renamed = !role.getName().equals(request.name());
        if (renamed && SystemRoles.isSystem(role.getName())) {
            throw error(HttpStatus.CONFLICT, "Los roles GUEST, HOST y ADMIN no se pueden renombrar");
        }
        if (renamed && roles.existsByName(request.name())) {
            throw error(HttpStatus.CONFLICT, "Ya existe un rol con ese nombre");
        }
        role.update(request.name(), blankToNull(request.description()));
        return toResponse(roles.save(role));
    }

    @Transactional
    public void delete(UUID id) {
        Role role = find(id);
        if (SystemRoles.isSystem(role.getName())) {
            throw error(HttpStatus.CONFLICT, "Los roles GUEST, HOST y ADMIN no se pueden borrar");
        }
        if (profileRoles.existsByIdRoleId(id)) {
            throw error(HttpStatus.CONFLICT, "El rol está asignado a uno o más perfiles");
        }
        roles.delete(role);
    }

    private Role find(UUID id) {
        return roles.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Rol no encontrado"));
    }

    private static RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getName(), role.getDescription(),
                SystemRoles.isSystem(role.getName()), role.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
