package com.xelastay.notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Aviso para un usuario (tabla notifications de V1). profile_id es una columna UUID simple, sin relación JPA
 * con Profile (entidad de Joshua). El ID lo asigna el backend: para notificaciones que vienen de eventos se
 * calcula a partir del evento, lo que hace idempotente al consumidor.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "title", nullable = false, length = 180)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "read_at", columnDefinition = "timestamptz")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    protected Notification() {
    }

    /** Notificación nueva sin leer (las de eventos se insertan con NotificationRepository#insertIfAbsent). */
    public static Notification create(UUID id, UUID profileId, String type, String title, String message) {
        Notification notification = new Notification();
        notification.id = id;
        notification.profileId = profileId;
        notification.type = type;
        notification.title = title;
        notification.message = message;
        notification.createdAt = Instant.now();
        return notification;
    }

    /** Marca la notificación como leída; si ya lo estaba, conserva la fecha original. */
    public void markRead(Instant when) {
        if (readAt == null) {
            readAt = when;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
