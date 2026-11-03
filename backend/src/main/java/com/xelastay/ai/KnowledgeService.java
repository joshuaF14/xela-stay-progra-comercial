package com.xelastay.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.xelastay.ai.dto.IngestResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Ingesta de documentos: valida, divide en fragmentos y guarda documento y fragmentos en una transacción. */
@Service
public class KnowledgeService {

    private final KnowledgeDocumentRepository documents;
    private final KnowledgeChunkRepository chunks;
    private final AdminGuard adminGuard;
    private final AiLogWriter logWriter;
    private final AiProperties properties;

    public KnowledgeService(KnowledgeDocumentRepository documents, KnowledgeChunkRepository chunks,
            AdminGuard adminGuard, AiLogWriter logWriter, AiProperties properties) {
        this.documents = documents;
        this.chunks = chunks;
        this.adminGuard = adminGuard;
        this.logWriter = logWriter;
        this.properties = properties;
    }

    @Transactional
    public IngestResponse ingest(UUID adminId, String title, String documentType, String source, String content,
            String ipAddress, String userAgent) {
        adminGuard.requireAdmin(adminId);
        String cleanTitle = required(title, "El título es obligatorio", 200);
        String cleanType = required(documentType, "El tipo de documento es obligatorio", 50).toUpperCase(Locale.ROOT);
        String cleanSource = source == null || source.isBlank() ? "api" : source.trim();

        List<String> parts = TextChunker.split(content, properties.chunkWords(), properties.chunkOverlapWords());
        if (parts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El documento no tiene texto");
        }

        KnowledgeDocument document = documents.save(KnowledgeDocument.indexed(cleanTitle, cleanSource, cleanType));
        List<KnowledgeChunk> rows = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            rows.add(KnowledgeChunk.of(document.getId(), i, parts.get(i), Map.of(
                    "title", cleanTitle,
                    "documentType", cleanType,
                    "words", TextChunker.countWords(parts.get(i)))));
        }
        chunks.saveAll(rows);

        logWriter.audit(adminId, "AI_DOCUMENT_INGESTED", "knowledge_document", document.getId(),
                Map.of("title", cleanTitle, "documentType", cleanType, "chunks", rows.size()), "ADMIN",
                ipAddress, userAgent);
        return new IngestResponse(document.getId(), cleanTitle, cleanType, cleanSource, document.getStatus(),
                rows.size());
    }

    private static String required(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        String clean = value.trim();
        if (clean.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    message.replace("es obligatorio", "admite máximo " + maxLength + " caracteres"));
        }
        return clean;
    }
}
