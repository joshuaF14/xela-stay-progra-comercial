package com.xelastay.messaging;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.xelastay.booking.PropertyInfo;
import com.xelastay.booking.PropertyReader;
import com.xelastay.common.PageResponse;
import com.xelastay.common.ProfileNameReader;
import com.xelastay.messaging.dto.ConversationResponse;
import com.xelastay.messaging.dto.ConversationResult;
import com.xelastay.messaging.dto.MarkReadResponse;
import com.xelastay.messaging.dto.MessageResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConversationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ConversationRepository conversations;
    private final ConversationParticipantRepository participants;
    private final MessageRepository messages;
    private final PropertyReader properties;
    private final ProfileNameReader profileNames;

    public ConversationService(ConversationRepository conversations, ConversationParticipantRepository participants,
            MessageRepository messages, PropertyReader properties, ProfileNameReader profileNames) {
        this.conversations = conversations;
        this.participants = participants;
        this.messages = messages;
        this.properties = properties;
        this.profileNames = profileNames;
    }

    /**
     * Abre la conversación entre el usuario (huésped) y el anfitrión del alojamiento, o devuelve la que ya
     * existe. Bloquea la propiedad para que dos clics seguidos no creen dos conversaciones.
     */
    @Transactional
    public ConversationResult start(UUID userId, UUID propertyId, String firstMessage) {
        PropertyInfo property = properties.lockPublished(propertyId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Alojamiento no encontrado o no disponible"));
        if (property.hostId().equals(userId)) {
            throw error(HttpStatus.BAD_REQUEST, "No puedes iniciar una conversación sobre tu propio alojamiento");
        }

        Optional<Conversation> existing = conversations.findBetween(property.id(), userId, property.hostId())
                .stream().findFirst();
        Conversation conversation = existing.orElseGet(() -> {
            Conversation created = conversations.save(Conversation.create(property.id()));
            participants.save(ConversationParticipant.of(created.getId(), userId));
            participants.save(ConversationParticipant.of(created.getId(), property.hostId()));
            return created;
        });

        String text = blankToNull(firstMessage);
        if (text != null) {
            messages.save(Message.create(conversation.getId(), userId, text));
            conversation.touch();
        }
        return new ConversationResult(describe(List.of(conversation), userId).get(0), existing.isEmpty());
    }

    /** Bandeja del usuario: conversaciones con actividad más reciente primero. */
    @Transactional(readOnly = true)
    public PageResponse<ConversationResponse> listMine(UUID userId, int page, int size) {
        Page<Conversation> result = conversations.findByParticipant(userId,
                pageable(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")));
        return PageResponse.of(result, describe(result.getContent(), userId));
    }

    /** Mensajes del más reciente al más antiguo (el frontend los invierte para mostrarlos). */
    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> messages(UUID userId, UUID conversationId, int page, int size) {
        requireParticipant(conversationId, userId);
        Page<Message> result = messages.findByConversationId(conversationId,
                pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(result, result.getContent().stream().map(MessageResponse::from).toList());
    }

    @Transactional
    public MessageResponse send(UUID userId, UUID conversationId, String content) {
        Conversation conversation = requireParticipant(conversationId, userId);
        String text = blankToNull(content);
        if (text == null) {
            throw error(HttpStatus.BAD_REQUEST, "El mensaje no puede estar vacío");
        }
        Message message = messages.save(Message.create(conversationId, userId, text));
        conversation.touch();
        return MessageResponse.from(message);
    }

    /** Marca como leídos los mensajes que el usuario recibió en la conversación. */
    @Transactional
    public MarkReadResponse markRead(UUID userId, UUID conversationId) {
        requireParticipant(conversationId, userId);
        return new MarkReadResponse(messages.markReadFor(conversationId, userId, Instant.now()));
    }

    private Conversation requireParticipant(UUID conversationId, UUID userId) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Conversación no encontrada"));
        if (!participants.existsByConversationIdAndProfileId(conversationId, userId)) {
            throw error(HttpStatus.FORBIDDEN, "No participas en esta conversación");
        }
        return conversation;
    }

    private List<ConversationResponse> describe(List<Conversation> page, UUID userId) {
        if (page.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = page.stream().map(Conversation::getId).collect(Collectors.toSet());
        Map<UUID, UUID> otherByConversation = participants.findByConversationIdIn(ids).stream()
                .filter(p -> !p.getProfileId().equals(userId))
                .collect(Collectors.toMap(ConversationParticipant::getConversationId,
                        ConversationParticipant::getProfileId, (first, second) -> first));
        Map<UUID, String> names = profileNames.findNames(Set.copyOf(otherByConversation.values()));
        Map<UUID, PropertyInfo> propertyById = properties.findByIds(
                page.stream().map(Conversation::getPropertyId).collect(Collectors.toSet()));

        return page.stream().map(conversation -> {
            UUID otherId = otherByConversation.get(conversation.getId());
            PropertyInfo property = propertyById.get(conversation.getPropertyId());
            MessageResponse last = messages.findFirstByConversationIdOrderByCreatedAtDesc(conversation.getId())
                    .map(MessageResponse::from)
                    .orElse(null);
            return new ConversationResponse(conversation.getId(), conversation.getPropertyId(),
                    property != null ? property.title() : null, otherId,
                    otherId != null ? names.get(otherId) : null, last,
                    messages.countUnreadFor(conversation.getId(), userId),
                    conversation.getCreatedAt(), conversation.getUpdatedAt());
        }).toList();
    }

    private static Pageable pageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
