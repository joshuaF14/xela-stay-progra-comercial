package com.xelastay.messaging;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Participante de una conversación (tabla conversation_participants, llave compuesta). */
@Entity
@Table(name = "conversation_participants")
@IdClass(ConversationParticipant.Key.class)
public class ConversationParticipant {

    @Id
    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Id
    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "joined_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant joinedAt;

    protected ConversationParticipant() {
    }

    public static ConversationParticipant of(UUID conversationId, UUID profileId) {
        ConversationParticipant participant = new ConversationParticipant();
        participant.conversationId = conversationId;
        participant.profileId = profileId;
        participant.joinedAt = Instant.now();
        return participant;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    /** Llave compuesta (conversation_id, profile_id). */
    public static class Key implements Serializable {

        private UUID conversationId;
        private UUID profileId;

        public Key() {
        }

        public Key(UUID conversationId, UUID profileId) {
            this.conversationId = conversationId;
            this.profileId = profileId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(conversationId, key.conversationId)
                    && Objects.equals(profileId, key.profileId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(conversationId, profileId);
        }
    }
}
