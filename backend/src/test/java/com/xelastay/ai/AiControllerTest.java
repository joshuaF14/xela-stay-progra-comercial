package com.xelastay.ai;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.xelastay.ai.dto.ChatResponse;
import com.xelastay.ai.dto.IngestResponse;
import com.xelastay.ai.dto.SourceRef;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KnowledgeService knowledgeService;

    @MockitoBean
    private AiChatService chatService;

    private final UUID userId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();

    @Test
    void chatRequiresAToken() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"¿Hay parqueo?\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(chatService);
    }

    @Test
    void chatReturnsAnswerWithSources() throws Exception {
        when(chatService.chat(eq(userId), eq("¿Hay parqueo?"), any(), any())).thenReturn(new ChatResponse(
                UUID.randomUUID(), "Depende del alojamiento [1].", List.of(new SourceRef(1, SourceRef.DOCUMENT,
                        documentId, "Preguntas frecuentes", UUID.randomUUID(), 0, null, null, "Extracto"))));

        mockMvc.perform(post("/api/v1/ai/chat").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"¿Hay parqueo?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Depende del alojamiento [1]."))
                .andExpect(jsonPath("$.sources[0].ref").value(1))
                .andExpect(jsonPath("$.sources[0].documentTitle").value("Preguntas frecuentes"))
                .andExpect(jsonPath("$.sources[0].propertyId").doesNotExist());
    }

    @Test
    void blankQuestionIs400() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("question"));
        verifyNoInteractions(chatService);
    }

    @Test
    void chatStreamRequiresAToken() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat/stream").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"¿Hay parqueo?\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(chatService);
    }

    @Test
    void chatStreamSendsTokenEventsAndThenDone() throws Exception {
        UUID interactionId = UUID.randomUUID();
        when(chatService.chatStream(eq(userId), eq("¿Hay parqueo?"), any(), any(), any())).thenAnswer(call -> {
            Consumer<String> onToken = call.getArgument(4);
            onToken.accept("Depende del ");
            onToken.accept("alojamiento [1].");
            return new ChatResponse(interactionId, "Depende del alojamiento [1].", List.of());
        });

        MvcResult started = mockMvc.perform(post("/api/v1/ai/chat/stream").with(user())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"¿Hay parqueo?\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith(MediaType.TEXT_EVENT_STREAM_VALUE)))
                .andExpect(content().string(containsString("event:token\ndata:{\"text\":\"Depende del \"}")))
                .andExpect(content().string(containsString("event:token\ndata:{\"text\":\"alojamiento [1].\"}")))
                .andExpect(content().string(containsString("event:done\ndata:{\"interactionId\":\"" + interactionId)));
    }

    @Test
    void chatStreamReportsModelErrorsAsAnErrorEvent() throws Exception {
        when(chatService.chatStream(any(), any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Límite de uso"));

        MvcResult started = mockMvc.perform(post("/api/v1/ai/chat/stream").with(user())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"¿Hay parqueo?\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(started))
                .andExpect(content().string(containsString("event:error\ndata:")))
                .andExpect(content().string(containsString("\"status\":503")))
                .andExpect(content().string(containsString("Límite de uso")));
    }

    @Test
    void ingestJsonReturns201() throws Exception {
        when(knowledgeService.ingest(eq(userId), eq("FAQ"), eq("FAQ"), isNull(), eq("Texto del documento"), any(),
                any())).thenReturn(response("FAQ"));

        mockMvc.perform(post("/api/v1/ai/documents").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"FAQ\",\"documentType\":\"FAQ\",\"content\":\"Texto del documento\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chunkCount").value(1));
    }

    @Test
    void ingestWithoutAdminRoleIs403() throws Exception {
        mockMvc.perform(post("/api/v1/ai/documents").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"FAQ\",\"documentType\":\"FAQ\",\"content\":\"Texto del documento\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(knowledgeService);
    }

    @Test
    void markdownUploadUsesTheFileNameAsTitle() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "preguntas-frecuentes.md", "text/markdown",
                "# Preguntas\nHola".getBytes(StandardCharsets.UTF_8));
        when(knowledgeService.ingest(eq(userId), eq("preguntas-frecuentes"), eq("FAQ"),
                eq("archivo:preguntas-frecuentes.md"), eq("# Preguntas\nHola"), any(), any()))
                .thenReturn(response("preguntas-frecuentes"));

        mockMvc.perform(multipart("/api/v1/ai/documents").file(file).param("documentType", "FAQ").with(admin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("preguntas-frecuentes"));
    }

    @Test
    void pdfUploadIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "manual.pdf", "application/pdf", new byte[] {1, 2});

        mockMvc.perform(multipart("/api/v1/ai/documents").file(file).param("documentType", "FAQ").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Solo se aceptan archivos .md o .txt"));
        verifyNoInteractions(knowledgeService);
    }

    private RequestPostProcessor user() {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private IngestResponse response(String title) {
        return new IngestResponse(documentId, title, "FAQ", "api", KnowledgeDocumentStatus.INDEXED, 1);
    }
}
