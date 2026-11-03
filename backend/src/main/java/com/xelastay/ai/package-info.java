/**
 * Módulo de IA/RAG (tablas knowledge_documents, knowledge_chunks, ai_interactions).
 * <p>
 * Responsable: Giovanni.
 * <p>
 * Ingesta de documentos en fragmentos, recuperación con búsqueda de texto completo de PostgreSQL (sin pgvector),
 * respuesta con Gemini citando fuentes y registro en ai_interactions y audit_logs (source = 'AI').
 */
package com.xelastay.ai;
