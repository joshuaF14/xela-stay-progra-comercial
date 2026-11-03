package com.xelastay.profile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.xelastay.audit.AuditService;
import com.xelastay.common.PageResponse;
import com.xelastay.common.Paging;
import com.xelastay.profile.dto.ProfileResponse;
import com.xelastay.profile.dto.UpdateMeRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Perfil propio (/me) y administración de perfiles y sus roles. */
@Service
public class ProfileService {

    private final ProfileRepository profiles;
    private final RoleRepository roles;
    private final ProfileRoleRepository profileRoles;
    private final AuditService audit;

    public ProfileService(ProfileRepository profiles, RoleRepository roles, ProfileRoleRepository profileRoles,
            AuditService audit) {
        this.profiles = profiles;
        this.roles = roles;
        this.profileRoles = profileRoles;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(UUID profileId) {
        Profile profile = find(profileId);
        return toResponse(profile, roleNames(List.of(profileId)).getOrDefault(profileId, List.of()));
    }

    /** Campos ausentes no cambian; en phone y avatarUrl, la cadena vacía borra el valor. */
    @Transactional
    public ProfileResponse updateMe(UUID profileId, UpdateMeRequest request) {
        Profile profile = find(profileId);
        Map<String, Object> before = contact(profile);
        if (request.fullName() != null) {
            profile.rename(request.fullName().trim());
        }
        if (request.phone() != null) {
            profile.changePhone(blankToNull(request.phone()));
        }
        if (request.avatarUrl() != null) {
            profile.changeAvatarUrl(blankToNull(request.avatarUrl()));
        }
        profiles.save(profile);
        audit.recordAction("PROFILE_UPDATED", "profile", profileId, before, contact(profile), profileId);
        return get(profileId);
    }

    /** Agrega el rol HOST al perfil propio. Si ya lo tiene, no hace nada. */
    @Transactional
    public ProfileResponse becomeHost(UUID profileId) {
        find(profileId);
        Role host = roles.findByName(SystemRoles.HOST)
                .orElseThrow(() -> new IllegalStateException("No existe el rol HOST: falta aplicar V2"));
        if (!profileRoles.existsById(new ProfileRoleId(profileId, host.getId()))) {
            profileRoles.save(ProfileRole.of(profileId, host));
            audit.recordAction("PROFILE_ROLE_ADDED", "profile", profileId, null,
                    Map.of("role", SystemRoles.HOST), profileId);
        }
        return get(profileId);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProfileResponse> search(ProfileStatus status, String role, int page, int size) {
        List<Specification<Profile>> filters = new ArrayList<>();
        if (status != null) {
            filters.add(ProfileRepository.hasStatus(status));
        }
        if (role != null && !role.isBlank()) {
            filters.add(ProfileRepository.hasRole(role.trim().toUpperCase(Locale.ROOT)));
        }
        Page<Profile> result = profiles.findAll(Specification.allOf(filters),
                Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<UUID, List<String>> names = roleNames(result.map(Profile::getId).getContent());
        return PageResponse.of(result, result.getContent().stream()
                .map(profile -> toResponse(profile, names.getOrDefault(profile.getId(), List.of())))
                .toList());
    }

    /** Un administrador no puede cambiar su propio estado (evita bloquearse a sí mismo). */
    @Transactional
    public ProfileResponse updateStatus(UUID adminId, UUID profileId, ProfileStatus status) {
        if (adminId.equals(profileId)) {
            throw error(HttpStatus.CONFLICT, "No puede cambiar el estado de su propio perfil");
        }
        Profile profile = find(profileId);
        ProfileStatus previous = profile.getStatus();
        profile.changeStatus(status);
        profiles.save(profile);
        audit.recordAction("PROFILE_STATUS_CHANGED", "profile", profileId, Map.of("status", previous.name()),
                Map.of("status", status.name()), profileId);
        return get(profileId);
    }

    /**
     * Reemplaza la lista de roles del perfil. Todos los nombres deben existir (400). Un administrador no puede
     * quitarse a sí mismo el rol ADMIN (409).
     */
    @Transactional
    public ProfileResponse replaceRoles(UUID adminId, UUID profileId, List<String> requestedNames) {
        find(profileId);
        Set<String> wanted = requestedNames.stream()
                .map(name -> name.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Role> found = roles.findByNameIn(wanted);
        if (found.size() != wanted.size()) {
            Set<String> existing = found.stream().map(Role::getName).collect(Collectors.toSet());
            List<String> unknown = wanted.stream().filter(name -> !existing.contains(name)).toList();
            throw error(HttpStatus.BAD_REQUEST, "Roles inexistentes: " + String.join(", ", unknown));
        }
        if (adminId.equals(profileId) && !wanted.contains(SystemRoles.ADMIN)) {
            throw error(HttpStatus.CONFLICT, "No puede quitarse su propio rol ADMIN");
        }

        List<ProfileRole> current = profileRoles.findWithRoleByProfileIds(List.of(profileId));
        Set<UUID> wantedIds = found.stream().map(Role::getId).collect(Collectors.toSet());
        Set<UUID> currentIds = current.stream().map(pr -> pr.getId().getRoleId()).collect(Collectors.toSet());
        profileRoles.deleteAll(current.stream().filter(pr -> !wantedIds.contains(pr.getId().getRoleId())).toList());
        profileRoles.saveAll(found.stream().filter(role -> !currentIds.contains(role.getId()))
                .map(role -> ProfileRole.of(profileId, role)).toList());
        List<String> before = current.stream().map(pr -> pr.getRole().getName()).sorted().toList();
        audit.recordAction("PROFILE_ROLES_REPLACED", "profile", profileId, Map.of("roles", before),
                Map.of("roles", wanted.stream().sorted().toList()), profileId);
        return get(profileId);
    }

    private Profile find(UUID profileId) {
        return profiles.findById(profileId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Perfil no encontrado"));
    }

    private Map<UUID, List<String>> roleNames(Collection<UUID> profileIds) {
        if (profileIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<String>> names = new HashMap<>();
        for (ProfileRole assignment : profileRoles.findWithRoleByProfileIds(profileIds)) {
            names.computeIfAbsent(assignment.getProfileId(), id -> new ArrayList<>())
                    .add(assignment.getRole().getName());
        }
        names.values().forEach(list -> list.sort(String::compareTo));
        return names;
    }

    /** Campos editables del perfil, para auditoría (sin email). */
    private static Map<String, Object> contact(Profile profile) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("fullName", profile.getFullName());
        values.put("phone", profile.getPhone());
        values.put("avatarUrl", profile.getAvatarUrl());
        return values;
    }

    private static ProfileResponse toResponse(Profile profile, List<String> roleNames) {
        return new ProfileResponse(profile.getId(), profile.getEmail(), profile.getFullName(), profile.getPhone(),
                profile.getAvatarUrl(), profile.getStatus(), roleNames, profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value.isBlank() ? null : value.trim();
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
