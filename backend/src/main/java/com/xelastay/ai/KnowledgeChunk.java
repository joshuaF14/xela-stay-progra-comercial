package com.xelastay.ai;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Fragmento de un documento (tabla knowledge_chunks de V1). Sin columna de embedding: la búsqueda es de texto
 * completo de PostgreSQL en español (ver {@link KnowledgeSearch}).
 */
@Entity
@Table(name = "knowledge_chunks")
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    protected KnowledgeChunk() {
    }

    public static KnowledgeChunk of(UUID documentId, int chunkIndex, String content, Map<String, Object> metadata) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.documentId = documentId;
        chunk.chunkIndex = chunkIndex;
        chunk.content = content;
        chunk.metadata = metadata;
        chunk.createdAt = Instant.now();
        return chunk;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
