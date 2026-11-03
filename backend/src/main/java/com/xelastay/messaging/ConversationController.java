package com.xelastay.messaging;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import com.xelastay.common.PageResponse;
import com.xelastay.messaging.dto.ConversationResponse;
import com.xelastay.messaging.dto.ConversationResult;
import com.xelastay.messaging.dto.MarkReadResponse;
import com.xelastay.messaging.dto.MessageResponse;
import com.xelastay.messaging.dto.SendMessageRequest;
import com.xelastay.messaging.dto.StartConversationRequest;
import com.xelastay.security.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ConversationController {

    private final ConversationService conversationService;
    private final CurrentUser currentUser;

    public ConversationController(ConversationService conversationService, CurrentUser currentUser) {
        this.conversationService = conversationService;
        this.currentUser = currentUser;
    }

    /** 201 si la conversación es nueva, 200 si ya existía. */
    @PostMapping("/conversations")
    public ResponseEntity<ConversationResponse> start(@Valid @RequestBody StartConversationRequest request) {
        ConversationResult result = conversationService.start(currentUser.requireId(), request.propertyId(),
                request.message());
        if (!result.created()) {
            return ResponseEntity.ok(result.conversation());
        }
        return ResponseEntity.created(URI.create("/api/v1/conversations/" + result.conversation().id()))
                .body(result.conversation());
    }

    @GetMapping("/me/conversations")
    public PageResponse<ConversationResponse> myConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return conversationService.listMine(currentUser.requireId(), page, size);
    }

    @GetMapping("/conversations/{id}/messages")
    public PageResponse<MessageResponse> messages(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return conversationService.messages(currentUser.requireId(), id, page, size);
    }

    @PostMapping("/conversations/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@PathVariable UUID id, @Valid @RequestBody SendMessageRequest request) {
        return conversationService.send(currentUser.requireId(), id, request.content());
    }

    @PatchMapping("/conversations/{id}/read")
    public MarkReadResponse markRead(@PathVariable UUID id) {
        return conversationService.markRead(currentUser.requireId(), id);
    }
}
