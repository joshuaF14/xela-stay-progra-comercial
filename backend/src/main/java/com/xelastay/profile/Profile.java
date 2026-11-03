package com.xelastay.profile;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Perfil de usuario (tabla profiles de V1). El id es el mismo UUID de auth.users (claim sub del JWT); el perfil
 * se crea con SQL nativo en {@link ProfileAccessService}, por eso la entidad no genera ids.
 */
@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "avatar_url", columnDefinition = "text")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProfileStatus status;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;

    protected Profile() {
    }

    /** Solo para pruebas y lecturas; en producción el perfil nace con el INSERT de ProfileAccessService. */
    public static Profile of(UUID id, String fullName, String email, ProfileStatus status) {
        Profile profile = new Profile();
        profile.id = id;
        profile.fullName = fullName;
        profile.email = email;
        profile.status = status;
        profile.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        profile.updatedAt = profile.createdAt;
        return profile;
    }

    public void rename(String fullName) {
        this.fullName = fullName;
    }

    public void changePhone(String phone) {
        this.phone = phone;
    }

    public void changeAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public void changeStatus(ProfileStatus status) {
        this.status = status;
    }

    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public ProfileStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
