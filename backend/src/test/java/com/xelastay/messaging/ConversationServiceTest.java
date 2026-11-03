package com.xelastay.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.xelastay.booking.PropertyInfo;
import com.xelastay.booking.PropertyReader;
import com.xelastay.common.ProfileNameReader;
import com.xelastay.messaging.dto.ConversationResult;
import com.xelastay.messaging.dto.MessageResponse;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversations;

    @Mock
    private ConversationParticipantRepository participants;

    @Mock
    private MessageRepository messages;

    @Mock
    private PropertyReader properties;

    @Mock
    private ProfileNameReader profileNames;

    private final UUID guestId = UUID.randomUUID();
    private final UUID hostId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private PropertyInfo property;
    private ConversationService service;

    @BeforeEach
    void setUp() {
        property = new PropertyInfo(propertyId, hostId, "Casa en Xela", new BigDecimal("350.00"), "GTQ", 4,
                "PUBLISHED");
        service = new ConversationService(conversations, participants, messages, properties, profileNames);
    }

    @Test
    void guestStartsConversationWithHostAndFirstMessage() {
        when(properties.lockPublished(propertyId)).thenReturn(Optional.of(property));
        when(conversations.findBetween(propertyId, guestId, hostId)).thenReturn(List.of());
        when(conversations.save(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", conversationId);
            return saved;
        });
        when(participants.findByConversationIdIn(any())).thenReturn(List.of(
                ConversationParticipant.of(conversationId, guestId),
                ConversationParticipant.of(conversationId, hostId)));
        when(profileNames.findNames(any())).thenReturn(Map.of(hostId, "Carlos Pérez"));
        when(properties.findByIds(any())).thenReturn(Map.of(propertyId, property));

        ConversationResult result = service.start(guestId, propertyId, " Hola, ¿tiene parqueo? ");

        assertThat(result.created()).isTrue();
        assertThat(result.conversation().id()).isEqualTo(conversationId);
        assertThat(result.conversation().otherParticipantId()).isEqualTo(hostId);
        assertThat(result.conversation().otherParticipantName()).isEqualTo("Carlos Pérez");
        assertThat(result.conversation().propertyTitle()).isEqualTo("Casa en Xela");

        ArgumentCaptor<ConversationParticipant> added = ArgumentCaptor.forClass(ConversationParticipant.class);
        verify(participants, times(2)).save(added.capture());
        assertThat(added.getAllValues()).extracting(ConversationParticipant::getProfileId)
                .containsExactlyInAnyOrder(guestId, hostId);
        ArgumentCaptor<Message> first = ArgumentCaptor.forClass(Message.class);
        verify(messages).save(first.capture());
        assertThat(first.getValue().getContent()).isEqualTo("Hola, ¿tiene parqueo?");
        assertThat(first.getValue().getSenderId()).isEqualTo(guestId);
    }

    @Test
    void existingConversationIsReused() {
        Conversation existing = conversation();
        when(properties.lockPublished(propertyId)).thenReturn(Optional.of(property));
        when(conversations.findBetween(propertyId, guestId, hostId)).thenReturn(List.of(existing));

        ConversationResult result = service.start(guestId, propertyId, null);

        assertThat(result.created()).isFalse();
        assertThat(result.conversation().id()).isEqualTo(conversationId);
        verify(conversations, never()).save(any());
        verify(participants, never()).save(any());
    }

    @Test
    void hostCannotStartConversationAboutOwnProperty() {
        when(properties.lockPublished(propertyId)).thenReturn(Optional.of(property));

        assertStatus(() -> service.start(hostId, propertyId, "Hola"), HttpStatus.BAD_REQUEST);
    }

    @Test
    void strangerCannotReadMessages() {
        UUID stranger = UUID.randomUUID();
        when(conversations.findById(conversationId)).thenReturn(Optional.of(conversation()));
        when(participants.existsByConversationIdAndProfileId(conversationId, stranger)).thenReturn(false);

        assertStatus(() -> service.messages(stranger, conversationId, 0, 50), HttpStatus.FORBIDDEN);
    }

    @Test
    void participantSendsMessageAndConversationMovesUp() {
        Conversation conversation = conversation();
        Instant before = conversation.getUpdatedAt();
        when(conversations.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(participants.existsByConversationIdAndProfileId(conversationId, hostId)).thenReturn(true);
        when(messages.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MessageResponse sent = service.send(hostId, conversationId, "  Sí, hay parqueo  ");

        assertThat(sent.content()).isEqualTo("Sí, hay parqueo");
        assertThat(sent.senderId()).isEqualTo(hostId);
        assertThat(conversation.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void markReadOnlyTouchesMessagesReceivedByTheUser() {
        when(conversations.findById(conversationId)).thenReturn(Optional.of(conversation()));
        when(participants.existsByConversationIdAndProfileId(conversationId, guestId)).thenReturn(true);
        when(messages.markReadFor(eq(conversationId), eq(guestId), any(Instant.class))).thenReturn(3);

        assertThat(service.markRead(guestId, conversationId).markedAsRead()).isEqualTo(3);
    }

    @Test
    void unknownConversationIsNotFound() {
        when(conversations.findById(conversationId)).thenReturn(Optional.empty());

        assertStatus(() -> service.send(guestId, conversationId, "Hola"), HttpStatus.NOT_FOUND);
        verify(messages, never()).save(any());
    }

    private Conversation conversation() {
        Conversation conversation = Conversation.create(propertyId);
        ReflectionTestUtils.setField(conversation, "id", conversationId);
        return conversation;
    }

    private static void assertStatus(ThrowingCallable call, HttpStatus expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(expected));
    }
}
