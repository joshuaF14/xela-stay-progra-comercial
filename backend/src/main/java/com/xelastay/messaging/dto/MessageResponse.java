package com.xelastay.messaging.dto;

import java.time.Instant;
import java.util.UUID;

import com.xelastay.messaging.Message;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String content,
        Instant readAt,
        Instant createdAt) {

    public static MessageResponse from(Message message) {
        return new MessageResponse(message.getId(), message.getConversationId(), message.getSenderId(),
                message.getContent(), message.getReadAt(), message.getCreatedAt());
    }
}
