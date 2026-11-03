package com.xelastay.messaging;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByConversationId(UUID conversationId, Pageable pageable);

    Optional<Message> findFirstByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    /** Mensajes sin leer que otros participantes le enviaron a {@code profileId}. */
    @Query("""
            select count(m) from Message m
            where m.conversationId = :conversationId and m.senderId <> :profileId and m.readAt is null
            """)
    long countUnreadFor(@Param("conversationId") UUID conversationId, @Param("profileId") UUID profileId);

    /** Marca como leídos los mensajes que recibió {@code profileId}; los propios no se tocan. */
    @Modifying
    @Query("""
            update Message m set m.readAt = :readAt
            where m.conversationId = :conversationId and m.senderId <> :profileId and m.readAt is null
            """)
    int markReadFor(@Param("conversationId") UUID conversationId, @Param("profileId") UUID profileId,
            @Param("readAt") Instant readAt);
}
