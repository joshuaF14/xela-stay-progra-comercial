package com.xelastay.notification;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Inserta la notificación solo si su ID no existe. Con IDs derivados del evento, procesar dos veces el
     * mismo mensaje no duplica avisos.
     *
     * @return 1 si se insertó, 0 si ya existía
     */
    @Modifying
    @Query(value = """
            INSERT INTO notifications (id, profile_id, type, title, message, created_at)
            VALUES (:id, :profileId, :type, :title, :message, now())
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("profileId") UUID profileId, @Param("type") String type,
            @Param("title") String title, @Param("message") String message);

    Page<Notification> findByProfileId(UUID profileId, Pageable pageable);

    Page<Notification> findByProfileIdAndReadAtIsNull(UUID profileId, Pageable pageable);

    Optional<Notification> findByIdAndProfileId(UUID id, UUID profileId);
}
