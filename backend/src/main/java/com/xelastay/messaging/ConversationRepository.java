package com.xelastay.messaging;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /** Conversación existente sobre el alojamiento en la que participan ambos usuarios. */
    @Query("""
            select c from Conversation c
            where c.propertyId = :propertyId
              and exists (select 1 from ConversationParticipant p
                          where p.conversationId = c.id and p.profileId = :firstId)
              and exists (select 1 from ConversationParticipant q
                          where q.conversationId = c.id and q.profileId = :secondId)
            """)
    List<Conversation> findBetween(@Param("propertyId") UUID propertyId, @Param("firstId") UUID firstId,
            @Param("secondId") UUID secondId);

    @Query(value = """
            select c from Conversation c
            where exists (select 1 from ConversationParticipant p
                          where p.conversationId = c.id and p.profileId = :profileId)
            """, countQuery = """
            select count(c) from Conversation c
            where exists (select 1 from ConversationParticipant p
                          where p.conversationId = c.id and p.profileId = :profileId)
            """)
    Page<Conversation> findByParticipant(@Param("profileId") UUID profileId, Pageable pageable);
}
