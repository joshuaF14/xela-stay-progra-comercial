package com.xelastay.messaging;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.xelastay.common.PageResponse;
import com.xelastay.messaging.dto.ConversationResponse;
import com.xelastay.messaging.dto.ConversationResult;
import com.xelastay.messaging.dto.MarkReadResponse;
import com.xelastay.messaging.dto.MessageResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    private final UUID userId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @Test
    void chatRequiresAToken() throws Exception {
        mockMvc.perform(get("/api/v1/me/conversations"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(conversationService);
    }

    @Test
    void newConversationReturns201AndExistingOneReturns200() throws Exception {
        when(conversationService.start(userId, propertyId, "Hola"))
                .thenReturn(new ConversationResult(conversation(), true))
                .thenReturn(new ConversationResult(conversation(), false));
        String body = "{\"propertyId\":\"" + propertyId + "\",\"message\":\"Hola\"}";

        mockMvc.perform(post("/api/v1/conversations").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/conversations/" + conversationId));
        mockMvc.perform(post("/api/v1/conversations").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otherParticipantName").value("Carlos Pérez"));
    }

    @Test
    void blankMessageIs400() throws Exception {
        mockMvc.perform(post("/api/v1/conversations/{id}/messages", conversationId).with(user())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("content"));
        verifyNoInteractions(conversationService);
    }

    @Test
    void sendMessageReturns201() throws Exception {
        when(conversationService.send(userId, conversationId, "¿A qué hora es el check-in?"))
                .thenReturn(message("¿A qué hora es el check-in?"));

        mockMvc.perform(post("/api/v1/conversations/{id}/messages", conversationId).with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"¿A qué hora es el check-in?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderId").value(userId.toString()));
    }

    @Test
    void messagesArePagedForTheCurrentUser() throws Exception {
        when(conversationService.messages(userId, conversationId, 0, 50))
                .thenReturn(new PageResponse<>(List.of(message("Hola")), 0, 50, 1, 1));

        mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("Hola"));
    }

    @Test
    void markReadReturnsHowManyMessagesChanged() throws Exception {
        when(conversationService.markRead(userId, conversationId)).thenReturn(new MarkReadResponse(2));

        mockMvc.perform(patch("/api/v1/conversations/{id}/read", conversationId).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedAsRead").value(2));
    }

    private RequestPostProcessor user() {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }

    private ConversationResponse conversation() {
        Instant now = Instant.now();
        return new ConversationResponse(conversationId, propertyId, "Casa en Xela", UUID.randomUUID(),
                "Carlos Pérez", null, 0, now, now);
    }

    private MessageResponse message(String content) {
        return new MessageResponse(UUID.randomUUID(), conversationId, userId, content, null, Instant.now());
    }
}
