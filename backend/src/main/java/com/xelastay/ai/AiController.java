package com.xelastay.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.xelastay.ai.dto.ChatRequest;
import com.xelastay.ai.dto.ChatResponse;
import com.xelastay.ai.dto.IngestDocumentRequest;
import com.xelastay.ai.dto.IngestResponse;
import com.xelastay.security.CurrentUser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    /** Tiempo máximo de una respuesta en streaming (Gemini corta a los 30 s sin datos). */
    static final long STREAM_TIMEOUT_MS = 120_000;

    private final KnowledgeService knowledgeService;
    private final AiChatService chatService;
    private final CurrentUser currentUser;
    /** Un hilo virtual por respuesta en streaming, para no ocupar los hilos del servidor mientras espera a Gemini. */
    private final ExecutorService streamExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public AiController(KnowledgeService knowledgeService, AiChatService chatService, CurrentUser currentUser) {
        this.knowledgeService = knowledgeService;
        this.chatService = chatService;
        this.currentUser = currentUser;
    }

    /** Ingesta en JSON (solo ADMIN). */
    @PostMapping(value = "/documents", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public IngestResponse ingest(@Valid @RequestBody IngestDocumentRequest request, HttpServletRequest http) {
        return knowledgeService.ingest(currentUser.requireId(), request.title(), request.documentType(),
                request.source(), request.content(), http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    }

    /** Ingesta de un archivo .md o .txt en UTF-8 (solo ADMIN). Sin título se usa el nombre del archivo. */
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public IngestResponse upload(@RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam String documentType,
            HttpServletRequest http) throws IOException {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lower = filename.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".md") && !lower.endsWith(".txt")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se aceptan archivos .md o .txt");
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        String effectiveTitle = title != null && !title.isBlank()
                ? title
                : filename.substring(0, filename.lastIndexOf('.'));
        return knowledgeService.ingest(currentUser.requireId(), effectiveTitle, documentType, "archivo:" + filename,
                content, http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    }

    /** Pregunta al agente. Responde solo con la base de conocimiento y los alojamientos publicados, citando fuentes. */
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, HttpServletRequest http) {
        return chatService.chat(currentUser.requireId(), request.question(), http.getRemoteAddr(),
                http.getHeader(HttpHeaders.USER_AGENT));
    }

    /**
     * Igual que /chat, pero la respuesta llega por partes como Server-Sent Events: "token" ({"text"}) mientras el
     * modelo escribe, "done" (la misma ChatResponse de /chat) al final, o "error" ({"status", "message"}).
     * El usuario, la IP y el user agent se leen aquí, en el hilo de la petición, porque el SecurityContext y el
     * HttpServletRequest no están disponibles en el hilo que genera la respuesta.
     */
    @PostMapping("/chat/stream")
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request, HttpServletRequest http) {
        UUID userId = currentUser.requireId();
        String ipAddress = http.getRemoteAddr();
        String userAgent = http.getHeader(HttpHeaders.USER_AGENT);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        streamExecutor.execute(() -> {
            try {
                ChatResponse response = chatService.chatStream(userId, request.question(), ipAddress, userAgent,
                        token -> send(emitter, "token", Map.of("text", token)));
                send(emitter, "done", response);
                emitter.complete();
            } catch (ResponseStatusException ex) {
                sendError(emitter, ex.getStatusCode().value(), ex.getReason());
            } catch (ClientGoneException ex) {
                log.debug("El cliente cerró el chat con streaming antes de terminar");
                emitter.complete();
            } catch (RuntimeException ex) {
                // Incluye IllegalStateException de SseEmitter: Spring envuelve así cualquier fallo al escribir.
                log.error("Error inesperado en el chat con streaming", ex);
                sendError(emitter, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error inesperado del agente");
            }
        });
        return emitter;
    }

    @PreDestroy
    void stopStreams() {
        streamExecutor.close();
    }

    private static void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            // El cliente cerró la conexión: se deja de generar.
            throw new ClientGoneException(ex);
        }
    }

    private static void sendError(SseEmitter emitter, int status, String message) {
        try {
            send(emitter, "error", Map.of("status", status, "message", message == null ? "Error del agente" : message));
        } catch (ClientGoneException | IllegalStateException ex) {
            log.debug("No se pudo enviar el evento de error del chat con streaming", ex);
        }
        emitter.complete();
    }

    /** El cliente ya no recibe eventos. */
    private static final class ClientGoneException extends RuntimeException {
        ClientGoneException(Throwable cause) {
            super(cause);
        }
    }
}
