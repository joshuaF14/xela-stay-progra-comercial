package com.xelastay.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

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
 * Cliente de la API de Gemini (generateContent) con el RestClient de Spring, sin SDK adicional.
 * La key viaja en el encabezado x-goog-api-key y nunca se escribe en logs.
 */
@Component
public class GeminiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final String TOOL_DESCRIPTION =
            "Obtiene la lista real y precios por noche de los alojamientos publicados en Xela Stay";

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
        List<Map<String, Object>> contents = new ArrayList<>();
        contents.add(userContent(prompt));
        return extractText(post(body(systemInstruction, contents, false)));
    }

    @Override
    public String generateWithTools(String systemInstruction, String prompt, AiToolExecutor executor) {
        List<Map<String, Object>> contents = new ArrayList<>();
        contents.add(userContent(prompt));

        JsonNode first = post(body(systemInstruction, contents, true));
        JsonNode functionCall = findFunctionCall(first);
        if (functionCall == null) {
            return extractText(first);
        }

        String name = functionCall.path("name").asText();
        if (!AiToolExecutor.CONSULTAR_ALOJAMIENTOS_DISPONIBLES.equals(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Gemini solicitó una herramienta no soportada: " + name);
        }
        JsonNode args = functionCall.path("args");
        Object arguments = args.isMissingNode() || args.isNull() ? Map.of() : args;
        String result = executor.execute(name);

        contents.add(Map.of("role", "model", "parts", List.of(Map.of("functionCall",
                Map.of("name", name, "args", arguments)))));
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("functionResponse",
                Map.of("name", name, "response", Map.of("result", result))))));

        return extractText(post(body(systemInstruction, contents, true)));
    }

    private Map<String, Object> body(String systemInstruction, List<Map<String, Object>> contents, boolean withTools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
        body.put("contents", contents);
        body.put("generationConfig", Map.of("temperature", 0.2, "maxOutputTokens", properties.maxOutputTokens()));
        if (withTools) {
            body.put("tools", List.of(Map.of("functionDeclarations", List.of(Map.of(
                    "name", AiToolExecutor.CONSULTAR_ALOJAMIENTOS_DISPONIBLES,
                    "description", TOOL_DESCRIPTION,
                    "parameters", Map.of("type", "OBJECT", "properties", Map.of()))))));
        }
        return body;
    }

    private static Map<String, Object> userContent(String text) {
        return Map.of("role", "user", "parts", List.of(Map.of("text", text)));
    }

    static JsonNode findFunctionCall(JsonNode response) {
        if (response == null) {
            return null;
        }
        for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
            JsonNode call = part.get("functionCall");
            if (call != null && call.hasNonNull("name")) {
                return call;
            }
        }
        return null;
    }

    private JsonNode post(Map<String, Object> body) {
        if (!properties.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El agente de IA no está configurado: falta AI_API_KEY en el .env");
        }
        try {
            return restClient.post()
                    .uri(properties.baseUrl() + "/models/{model}:generateContent", properties.model())
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                        int status = httpResponse.getStatusCode().value();
                        log.warn("Gemini respondió {} para el modelo {}", status, properties.model());
                        if (status == 429) {
                            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                                    "Se alcanzó el límite de uso de Gemini; intenta de nuevo en un minuto");
                        }
                        if (status == 404) {
                            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                    "Gemini no reconoce el modelo " + properties.model() + "; revisa AI_MODEL");
                        }
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "El proveedor de IA respondió con error " + status);
                    })
                    .body(JsonNode.class);
        } catch (ResourceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "El proveedor de IA no respondió a tiempo");
        }
    }

    /** Une las partes de texto del primer candidato (omite las de razonamiento interno, marcadas como thought). */
    static String extractText(JsonNode response) {
        StringBuilder text = new StringBuilder();
        if (response != null) {
            for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false) && part.hasNonNull("text")) {
                    text.append(part.get("text").asText());
                }
            }
        }
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El modelo no devolvió una respuesta");
        }
        return text.toString().strip();
    }

    private static SimpleClientHttpRequestFactory timeouts(AiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.timeout().toMillis());
        factory.setReadTimeout((int) properties.timeout().toMillis());
        return factory;
    }
}
