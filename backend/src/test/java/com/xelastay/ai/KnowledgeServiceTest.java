package com.xelastay.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.xelastay.ai.dto.IngestResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    @Mock
    private KnowledgeDocumentRepository documents;

    @Mock
    private KnowledgeChunkRepository chunks;

    @Mock
    private AiLogWriter logWriter;

    @Captor
    private ArgumentCaptor<List<KnowledgeChunk>> savedChunks;

    private final UUID adminId = UUID.randomUUID();
    private KnowledgeService service;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties("", "gemini-3.8-flash",
                "https://generativelanguage.googleapis.com/v1beta", Duration.ofSeconds(30), 2048, 5, 3, 800, 100);
        service = new KnowledgeService(documents, chunks, logWriter, properties);
    }

    @Test
    void adminIngestsDocumentAsOverlappingChunks() {
        UUID documentId = UUID.randomUUID();
        when(documents.save(any(KnowledgeDocument.class))).thenAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            ReflectionTestUtils.setField(document, "id", documentId);
            return document;
        });

        IngestResponse response = service.ingest(adminId, " Políticas de cancelación ", "politica", null,
                words(1700), "127.0.0.1", "curl");

        assertThat(response.documentId()).isEqualTo(documentId);
        assertThat(response.title()).isEqualTo("Políticas de cancelación");
        assertThat(response.documentType()).isEqualTo("POLITICA");
        assertThat(response.source()).isEqualTo("api");
        assertThat(response.status()).isEqualTo(KnowledgeDocumentStatus.INDEXED);
        assertThat(response.chunkCount()).isEqualTo(3);

        verify(chunks).saveAll(savedChunks.capture());
        assertThat(savedChunks.getValue()).extracting(KnowledgeChunk::getChunkIndex).containsExactly(0, 1, 2);
        assertThat(savedChunks.getValue()).allSatisfy(chunk -> {
            assertThat(chunk.getDocumentId()).isEqualTo(documentId);
            assertThat(chunk.getMetadata()).containsEntry("title", "Políticas de cancelación");
        });
        verify(logWriter).audit(eq(adminId), eq("AI_DOCUMENT_INGESTED"), eq("knowledge_document"), eq(documentId),
                any(), eq("ADMIN"), eq("127.0.0.1"), eq("curl"));
    }

    @Test
    void documentWithoutTextIsRejected() {
        assertThatThrownBy(() -> service.ingest(adminId, "FAQ", "FAQ", null, "   ", "127.0.0.1", null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(documents, never()).save(any());
    }

    private static String words(int count) {
        return IntStream.range(0, count).mapToObj(i -> "palabra" + i).collect(Collectors.joining(" "));
    }
}
