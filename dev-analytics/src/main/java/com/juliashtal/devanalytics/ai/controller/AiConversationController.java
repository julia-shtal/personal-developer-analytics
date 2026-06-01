package com.juliashtal.devanalytics.ai.controller;

import com.juliashtal.devanalytics.ai.model.*;
import com.juliashtal.devanalytics.ai.service.AiConversationService;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.user.model.User;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for AI follow-up conversations.
 * Mounted at /api/ai/conversations — lets users ask questions about a generated summary.
 */
@RestController
@RequestMapping("/api/ai/conversations")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class AiConversationController {

    private final AiConversationService conversationService;
    private final CheckHelper checkHelper;

    @Operation(summary = "Start a new follow-up conversation from an existing AI summary")
    @PostMapping
    public ResponseEntity<ConversationDto> startConversation(@RequestBody StartConversationRequest req) {
        User user = checkHelper.currentUser();
        ConversationDto dto = conversationService.startConversation(user, req.getSummaryScope(), req.getSummaryJson());
        return ResponseEntity.status(201).body(dto);
    }

    @Operation(summary = "Send a message and receive an AI response within an existing conversation")
    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageDto> sendMessage(
            @PathVariable Long id,
            @RequestBody SendMessageRequest req) {
        User user = checkHelper.currentUser();
        MessageDto dto = conversationService.sendMessage(user, id, req.getContent());
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Get all messages in a conversation (ordered oldest to newest)")
    @GetMapping("/{id}/messages")
    public ResponseEntity<List<MessageDto>> getMessages(@PathVariable Long id) {
        User user = checkHelper.currentUser();
        List<MessageDto> messages = conversationService.getMessages(user, id);
        return ResponseEntity.ok(messages);
    }
}
