package com.xelastay.profile;

import jakarta.validation.Valid;

import com.xelastay.profile.dto.ProfileResponse;
import com.xelastay.profile.dto.UpdateMeRequest;
import com.xelastay.security.CurrentUser;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Perfil del usuario autenticado. El perfil se crea automáticamente en la primera petición con token. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final ProfileService profileService;
    private final CurrentUser currentUser;

    public MeController(ProfileService profileService, CurrentUser currentUser) {
        this.profileService = profileService;
        this.currentUser = currentUser;
    }

    /** Disponible también para perfiles SUSPENDED o INACTIVE, para que vean su estado. */
    @GetMapping
    public ProfileResponse me() {
        return profileService.get(currentUser.requireId());
    }

    @PatchMapping
    public ProfileResponse update(@Valid @RequestBody UpdateMeRequest request) {
        return profileService.updateMe(currentUser.requireId(), request);
    }

    /** Convierte al usuario en anfitrión (agrega el rol HOST). */
    @PostMapping("/host")
    public ProfileResponse becomeHost() {
        return profileService.becomeHost(currentUser.requireId());
    }
}
