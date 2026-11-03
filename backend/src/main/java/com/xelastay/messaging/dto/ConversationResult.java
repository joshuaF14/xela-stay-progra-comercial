package com.xelastay.messaging.dto;

/** @param created false si ya existía una conversación entre ambos sobre ese alojamiento */
public record ConversationResult(ConversationResponse conversation, boolean created) {
}
