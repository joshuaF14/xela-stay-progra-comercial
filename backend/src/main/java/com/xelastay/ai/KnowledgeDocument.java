package com.xelastay.ai;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Documento de la base de conocimiento del agente (tabla knowledge_documents de V1). */
@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "source", nullable = false, columnDefinition = "text")
    private String source;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private KnowledgeDocumentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    protected KnowledgeDocument() {
    }

    public static KnowledgeDocument indexed(String title, String source, String documentType) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.title = title;
        document.source = source;
        document.documentType = documentType;
        document.status = KnowledgeDocumentStatus.INDEXED;
        document.createdAt = Instant.now();
        document.updatedAt = document.createdAt;
        return document;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSource() {
        return source;
    }

    public String getDocumentType() {
        return documentType;
    }

    public KnowledgeDocumentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
