package com.xelastay.ai.dto;

import java.util.UUID;

import com.xelastay.ai.KnowledgeDocumentStatus;

public record IngestResponse(UUID documentId, String title, String documentType, String source,
        KnowledgeDocumentStatus status, int chunkCount) {
}
