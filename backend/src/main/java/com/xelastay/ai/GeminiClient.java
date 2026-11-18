package com.xelastay.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Cliente de la API de Gemini (generateContent y streamGenerateContent) con el RestClient de Spring, sin SDK
 * adicional. La key viaja en el encabezado x-goog-api-key y nunca se escribe en logs.
 */
@Component
public class GeminiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SSE_DATA = "data:";

    private final RestClient restClient;
    private final AiProperties properties;

    @Autowired
    public GeminiClient(RestClient.Builder builder, AiProperties properties) {
        this(builder.requestFactory(timeouts(properties)).build(), properties);
    }

    GeminiClient(RestClient restClient, AiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String generate(String systemInstruction, String prompt) {
        requireConfigured();
        JsonNode response;
        try {
            response = restClient.post()
                    .uri(properties.baseUrl() + "/models/{model}:generateContent", properties.model())
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(systemInstruction, prompt))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                        throw providerError(httpResponse.getStatusCode().value());
                    })
                    .body(JsonNode.class);
        } catch (ResourceAccessException ex) {
            throw timeout();
        }
        return extractText(response);
    }

    /**
     * Pide la respuesta con streamGenerateContent?alt=sse: Gemini responde con eventos SSE, una línea
     * "data: {json}" por cada pedazo. Cada pedazo de texto se entrega a onToken en cuanto se lee.
     */
    @Override
    public String generateStream(String systemInstruction, String prompt, Consumer<String> onToken) {
        requireConfigured();
        try {
            return restClient.post()
                    .uri(properties.baseUrl() + "/models/{model}:streamGenerateContent?alt=sse", properties.model())
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(requestBody(systemInstruction, prompt))
                    .exchange((request, httpResponse) -> {
                        if (httpResponse.getStatusCode().isError()) {
                            throw providerError(httpResponse.getStatusCode().value());
                        }
                        return readStream(httpResponse.getBody(), onToken);
                    });
        } catch (ResourceAccessException ex) {
            throw timeout();
        }
    }

    /** Lee las líneas "data:" del flujo, entrega cada pedazo de texto y devuelve el texto completo. */
    static String readStream(InputStream body, Consumer<String> onToken) throws IOException {
        StringBuilder answer = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.startsWith(SSE_DATA)) {
                continue;
            }
            String data = line.substring(SSE_DATA.length()).strip();
            if (data.isEmpty()) {
                continue;
            }
            String piece;
            try {
                piece = partsText(JSON.readTree(data));
            } catch (JsonProcessingException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El proveedor de IA envió datos inválidos");
            }
            if (!piece.isEmpty()) {
                onToken.accept(piece);
                answer.append(piece);
            }
        }
        if (answer.toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El modelo no devolvió una respuesta");
        }
        return answer.toString().strip();
    }

    /** Une las partes de texto del primer candidato (omite las de razonamiento interno, marcadas como thought). */
    static String extractText(JsonNode response) {
        String text = partsText(response);
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El modelo no devolvió una respuesta");
        }
        return text.strip();
    }

    private static String partsText(JsonNode response) {
        StringBuilder text = new StringBuilder();
        if (response != null) {
            for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false) && part.hasNonNull("text")) {
                    text.append(part.get("text").asText());
                }
            }
        }
        return text.toString();
    }

    private Map<String, Object> requestBody(String systemInstruction, String prompt) {
        return Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature", 0.2, "maxOutputTokens", properties.maxOutputTokens()));
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El agente de IA no está configurado: falta AI_API_KEY en el .env");
        }
    }

    private ResponseStatusException providerError(int status) {
        log.warn("Gemini respondió {} para el modelo {}", status, properties.model());
        if (status == 429) {
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Se alcanzó el límite de uso de Gemini; intenta de nuevo en un minuto");
        }
        if (status == 404) {
            return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Gemini no reconoce el modelo " + properties.model() + "; revisa AI_MODEL");
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El proveedor de IA respondió con error " + status);
    }

    private static ResponseStatusException timeout() {
        return new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "El proveedor de IA no respondió a tiempo");
    }

    private static SimpleClientHttpRequestFactory timeouts(AiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.timeout().toMillis());
        factory.setReadTimeout((int) properties.timeout().toMillis());
        return factory;
    }
}
