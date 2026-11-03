package com.xelastay.profile;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileRoleRepository extends JpaRepository<ProfileRole, ProfileRoleId> {

    @Query("select pr from ProfileRole pr join fetch pr.role where pr.id.profileId in :profileIds")
    List<ProfileRole> findWithRoleByProfileIds(@Param("profileIds") Collection<UUID> profileIds);

    boolean existsByIdRoleId(UUID roleId);
}
