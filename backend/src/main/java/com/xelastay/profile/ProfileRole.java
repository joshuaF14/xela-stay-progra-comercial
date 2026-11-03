package com.xelastay.profile;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/** Asignación de un rol a un perfil (tabla profile_roles de V1). */
@Entity
@Table(name = "profile_roles")
public class ProfileRole {

    @EmbeddedId
    private ProfileRoleId id;

    @MapsId("roleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(name = "assigned_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime assignedAt;

    protected ProfileRole() {
    }

    public static ProfileRole of(UUID profileId, Role role) {
        ProfileRole assignment = new ProfileRole();
        assignment.id = new ProfileRoleId(profileId, role.getId());
        assignment.role = role;
        assignment.assignedAt = OffsetDateTime.now(ZoneOffset.UTC);
        return assignment;
    }

    public ProfileRoleId getId() {
        return id;
    }

    public UUID getProfileId() {
        return id.getProfileId();
    }

    public Role getRole() {
        return role;
    }

    public OffsetDateTime getAssignedAt() {
        return assignedAt;
    }
}
