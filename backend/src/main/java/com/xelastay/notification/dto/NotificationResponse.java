package com.xelastay.notification.dto;

import java.time.Instant;
import java.util.UUID;

import com.xelastay.notification.Notification;

/** @param readAt null si la notificación no se ha leído */
public record NotificationResponse(
        UUID id,
        String type,
        String title,
        String message,
        Instant readAt,
        Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getMessage(), notification.getReadAt(), notification.getCreatedAt());
    }
}
