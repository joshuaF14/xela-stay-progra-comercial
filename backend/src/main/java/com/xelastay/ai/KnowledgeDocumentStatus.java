package com.xelastay.ai;

/**
 * Estados de knowledge_documents.status (CHECK de V1). La ingesta es síncrona: el documento queda INDEXED en la
 * misma transacción en que se guardan sus fragmentos.
 */
public enum KnowledgeDocumentStatus {
    PENDING,
    PROCESSING,
    INDEXED,
    FAILED
}
