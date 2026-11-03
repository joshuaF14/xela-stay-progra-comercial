package com.xelastay.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.xelastay.ai.dto.ChatRequest;
import com.xelastay.ai.dto.ChatResponse;
import com.xelastay.ai.dto.IngestDocumentRequest;
import com.xelastay.ai.dto.IngestResponse;
import com.xelastay.security.CurrentUser;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final KnowledgeService knowledgeService;
    private final AiChatService chatService;
    private final CurrentUser currentUser;

    public AiController(KnowledgeService knowledgeService, AiChatService chatService, CurrentUser currentUser) {
        this.knowledgeService = knowledgeService;
        this.chatService = chatService;
        this.currentUser = currentUser;
    }

    /** Ingesta en JSON (solo ADMIN). */
    @PostMapping(value = "/documents", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public IngestResponse ingest(@Valid @RequestBody IngestDocumentRequest request, HttpServletRequest http) {
        return knowledgeService.ingest(currentUser.requireId(), request.title(), request.documentType(),
                request.source(), request.content(), http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    }

    /** Ingesta de un archivo .md o .txt en UTF-8 (solo ADMIN). Sin título se usa el nombre del archivo. */
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
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
}
