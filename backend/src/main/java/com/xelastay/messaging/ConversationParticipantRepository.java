package com.xelastay.messaging;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationParticipantRepository
        extends JpaRepository<ConversationParticipant, ConversationParticipant.Key> {

    boolean existsByConversationIdAndProfileId(UUID conversationId, UUID profileId);

    List<ConversationParticipant> findByConversationIdIn(Collection<UUID> conversationIds);
}
