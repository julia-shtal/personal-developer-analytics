package com.juliashtal.devanalytics.messaging;

import com.juliashtal.devanalytics.messaging.dto.DirectMessageDto;
import com.juliashtal.devanalytics.messaging.dto.InboxEntryDto;
import com.juliashtal.devanalytics.messaging.dto.SendMessageRequest;
import com.juliashtal.devanalytics.messaging.dto.UnreadCountDto;
import com.juliashtal.devanalytics.messaging.service.MessageService;
import com.juliashtal.devanalytics.security.CheckHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for direct messages.
 * Mounted at /api/messages — 1:1 messaging between team members.
 */
@RestController
@RequestMapping("/api/messages")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Tag(name = "Messages", description = "1:1 direct messaging between team members")
public class MessageController {

    private final MessageService messageService;
    private final CheckHelper checkHelper;

    @PostMapping
    @Operation(summary = "Send a direct message to a teammate")
    public ResponseEntity<DirectMessageDto> send(@Valid @RequestBody SendMessageRequest req) {
        return ResponseEntity.ok(messageService.send(checkHelper.currentUser(), req.recipientId(), req.body()));
    }

    @GetMapping("/conversations")
    @Operation(summary = "List all conversations (inbox) with latest message and unread count per partner")
    public List<InboxEntryDto> inbox() {
        return messageService.inbox(checkHelper.currentUser());
    }

    @GetMapping("/conversations/{userId}")
    @Operation(summary = "Get paginated message thread with a specific user; marks incoming messages as read")
    public List<DirectMessageDto> conversation(
            @Parameter(description = "ID of the other user in the conversation") @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return messageService.conversation(checkHelper.currentUser(), userId, page, size);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Total number of unread messages for the current user")
    public UnreadCountDto unreadCount() {
        return messageService.unreadCount(checkHelper.currentUser());
    }
}
