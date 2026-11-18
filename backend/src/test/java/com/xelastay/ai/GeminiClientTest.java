package com.xelastay.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** Formato de la llamada a Gemini y manejo de errores, sin red (MockRestServiceServer). */
class GeminiClientTest {

    private static final String URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent";
    private static final String STREAM_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:streamGenerateContent?alt=sse";

    @Test
    void sendsTheKeyInTheHeaderAndReturnsOnlyTheAnswerText() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiClient client = new GeminiClient(builder.build(), properties("clave-de-prueba"));
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "clave-de-prueba"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("Reglas"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Pregunta"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[
                          {"text":"razonamiento interno","thought":true},
                          {"text":"Puedes cancelar sin costo hasta 48 horas antes [1]."}
                        ]}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.generate("Reglas", "Pregunta"))
                .isEqualTo("Puedes cancelar sin costo hasta 48 horas antes [1].");
        server.verify();
    }

    @Test
    void rateLimitBecomes503() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiClient client = new GeminiClient(builder.build(), properties("clave-de-prueba"));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.generate("Reglas", "Pregunta"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void withoutApiKeyItAnswers503WithoutCallingGemini() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiClient client = new GeminiClient(builder.build(), properties("<api-key-de-google-ai-studio>"));

        assertThatThrownBy(() -> client.generate("Reglas", "Pregunta"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        server.verify();
    }

    @Test
    void streamReadsSseDataLinesAndDeliversEachPieceInOrder() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiClient client = new GeminiClient(builder.build(), properties("clave-de-prueba"));
        server.expect(requestTo(STREAM_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "clave-de-prueba"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Pregunta"))
                .andRespond(withSuccess("""
                        data: {"candidates":[{"content":{"parts":[{"text":"pensando","thought":true}]}}]}

                        data: {"candidates":[{"content":{"parts":[{"text":"Puedes cancelar "}]}}]}

                        data: {"candidates":[{"content":{"parts":[{"text":"sin costo [1]."}]}}]}

                        """, MediaType.TEXT_EVENT_STREAM));

        List<String> pieces = new ArrayList<>();
        String answer = client.generateStream("Reglas", "Pregunta", pieces::add);

        assertThat(pieces).containsExactly("Puedes cancelar ", "sin costo [1].");
        assertThat(answer).isEqualTo("Puedes cancelar sin costo [1].");
        server.verify();
    }

    @Test
    void streamRateLimitBecomes503WithoutTokens() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiClient client = new GeminiClient(builder.build(), properties("clave-de-prueba"));
        server.expect(requestTo(STREAM_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        List<String> pieces = new ArrayList<>();
        assertThatThrownBy(() -> client.generateStream("Reglas", "Pregunta", pieces::add))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(pieces).isEmpty();
    }

    @Test
    void streamWithoutTextIs502() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiClient client = new GeminiClient(builder.build(), properties("clave-de-prueba"));
        server.expect(requestTo(STREAM_URL)).andRespond(withSuccess("""
                data: {"candidates":[{"finishReason":"SAFETY"}]}

                """, MediaType.TEXT_EVENT_STREAM));

        assertThatThrownBy(() -> client.generateStream("Reglas", "Pregunta", piece -> { }))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    private static AiProperties properties(String apiKey) {
        return new AiProperties(apiKey, "gemini-3.8-flash", "https://generativelanguage.googleapis.com/v1beta",
                Duration.ofSeconds(5), 2048, 5, 3, 800, 100);
    }
}
