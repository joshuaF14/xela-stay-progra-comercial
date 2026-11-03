package com.xelastay.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.xelastay.ai.dto.ChatResponse;
import com.xelastay.ai.dto.IngestResponse;
import com.xelastay.ai.dto.SourceRef;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

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
