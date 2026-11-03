package com.xelastay.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del agente de IA (prefijo xelastay.ai). La API key llega solo por la variable AI_API_KEY.
 *
 * @param apiKey            key de Google AI Studio; vacía = agente desactivado (el chat responde 503)
 * @param model             modelo de Gemini, p. ej. gemini-3.8-flash o gemini-3.5-flash-lite
 * @param maxChunks         fragmentos de documentos que se envían al modelo
 * @param maxProperties     alojamientos publicados que se agregan como fuente
 * @param chunkWords        palabras por fragmento al indexar
 * @param chunkOverlapWords palabras que se repiten entre fragmentos seguidos
 */
@ConfigurationProperties("xelastay.ai")
public record AiProperties(
        String apiKey,
        @DefaultValue("gemini-3.8-flash") String model,
        @DefaultValue("https://generativelanguage.googleapis.com/v1beta") String baseUrl,
        @DefaultValue("30s") Duration timeout,
        @DefaultValue("2048") int maxOutputTokens,
        @DefaultValue("5") int maxChunks,
        @DefaultValue("3") int maxProperties,
        @DefaultValue("800") int chunkWords,
        @DefaultValue("100") int chunkOverlapWords) {

    /** true si hay una key real (no vacía ni el marcador de .env.example). */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("<");
    }
}
