package com.xelastay.profile;

import java.util.UUID;

import jakarta.validation.Valid;

import com.xelastay.common.PageResponse;
import com.xelastay.profile.dto.ProfileResponse;
import com.xelastay.profile.dto.UpdateProfileRolesRequest;
import com.xelastay.profile.dto.UpdateProfileStatusRequest;
import com.xelastay.security.CurrentUser;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administración de perfiles (solo ADMIN). */
@RestController
@RequestMapping("/api/v1/profiles")
@PreAuthorize("hasRole('ADMIN')")
public class ProfileAdminController {

    private final ProfileService profileService;
    private final CurrentUser currentUser;

    public ProfileAdminController(ProfileService profileService, CurrentUser currentUser) {
        this.profileService = profileService;
        this.currentUser = currentUser;
    }

    /** Más recientes primero; filtros opcionales por estado y por nombre de rol. */
    @GetMapping
    public PageResponse<ProfileResponse> search(
            @RequestParam(required = false) ProfileStatus status,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return profileService.search(status, role, page, size);
    }

    @GetMapping("/{id}")
    public ProfileResponse get(@PathVariable UUID id) {
        return profileService.get(id);
    }

    @PatchMapping("/{id}/status")
    public ProfileResponse updateStatus(@PathVariable UUID id,
            @Valid @RequestBody UpdateProfileStatusRequest request) {
        return profileService.updateStatus(currentUser.requireId(), id, request.status());
    }

    @PutMapping("/{id}/roles")
    public ProfileResponse replaceRoles(@PathVariable UUID id, @Valid @RequestBody UpdateProfileRolesRequest request) {
        return profileService.replaceRoles(currentUser.requireId(), id, request.roles());
    }
}
