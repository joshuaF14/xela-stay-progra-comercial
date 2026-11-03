package com.xelastay.messaging;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Conversación sobre un alojamiento (tabla conversations de V1). property_id es un UUID simple. */
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    protected Conversation() {
    }

    public static Conversation create(UUID propertyId) {
        Conversation conversation = new Conversation();
        conversation.propertyId = propertyId;
        conversation.createdAt = Instant.now();
        conversation.updatedAt = conversation.createdAt;
        return conversation;
    }

    /** Marca actividad reciente (nuevo mensaje) para ordenar la bandeja. */
    public void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
