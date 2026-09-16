package com.juliashtal.devanalytics.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.config.SecurityConfig;
import com.juliashtal.devanalytics.messaging.controller.MessageController;
import com.juliashtal.devanalytics.messaging.dto.DirectMessageDto;
import com.juliashtal.devanalytics.messaging.dto.InboxEntryDto;
import com.juliashtal.devanalytics.messaging.dto.SendMessageRequest;
import com.juliashtal.devanalytics.messaging.dto.UnreadCountDto;
import com.juliashtal.devanalytics.messaging.service.MessageService;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.security.JwtAuthFilter;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice tests for {@link MessageController}.
 *
 * <p>The sender is always the resolved caller, never a request field, and
 * {@code SendMessageRequest} is validated at the boundary — both are pinned here.</p>
 */
@WebMvcTest(MessageController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class MessageControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean MessageService messageService;
    @MockBean CheckHelper checkHelper;
    // Required by SecurityConfig / JwtAuthFilter when filters are active
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;
    // Required by ActivityInterceptor (HandlerInterceptor picked up by @WebMvcTest)
    @MockBean UserService userService;

    private User sender;

    @BeforeEach
    void setUp() {
        sender = new User();
        sender.setId(1L);
        sender.setUsername("alice");
        when(checkHelper.currentUser()).thenReturn(sender);
    }

    @Test
    @WithMockUser
    void send_validRequest_returns200AndUsesResolvedSender() throws Exception {
        SendMessageRequest req = new SendMessageRequest(2L, "morning");
        when(messageService.send(sender, 2L, "morning")).thenReturn(new DirectMessageDto(
                7L, 1L, 2L, "morning", Instant.parse("2026-09-01T08:00:00Z"), null));

        mockMvc.perform(post("/api/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.senderId").value(1));

        verify(messageService).send(sender, 2L, "morning");
    }

    @Test
    @WithMockUser
    void send_blankBody_returns400() throws Exception {
        SendMessageRequest req = new SendMessageRequest(2L, "  ");

        mockMvc.perform(post("/api/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void send_noRecipient_returns400() throws Exception {
        SendMessageRequest req = new SendMessageRequest(null, "morning");

        mockMvc.perform(post("/api/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void inbox_authenticated_returns200() throws Exception {
        when(messageService.inbox(sender)).thenReturn(List.of(new InboxEntryDto(
                2L, "bob", "preset-03", false, "morning",
                Instant.parse("2026-09-01T08:00:00Z"), 1L, 0L)));

        mockMvc.perform(get("/api/messages/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].partnerUsername").value("bob"));
    }

    @Test
    @WithMockUser
    void conversation_defaultPaging_requestsFirstPageOf50() throws Exception {
        when(messageService.conversation(sender, 2L, 0, 50)).thenReturn(List.of());

        mockMvc.perform(get("/api/messages/conversations/2"))
                .andExpect(status().isOk());

        verify(messageService).conversation(sender, 2L, 0, 50);
    }

    @Test
    @WithMockUser
    void conversation_explicitPaging_passesPageAndSizeThrough() throws Exception {
        when(messageService.conversation(sender, 2L, 3, 10)).thenReturn(List.of());

        mockMvc.perform(get("/api/messages/conversations/2").param("page", "3").param("size", "10"))
                .andExpect(status().isOk());

        verify(messageService).conversation(sender, 2L, 3, 10);
    }

    @Test
    @WithMockUser
    void unreadCount_authenticated_returnsCount() throws Exception {
        when(messageService.unreadCount(sender)).thenReturn(new UnreadCountDto(4L));

        mockMvc.perform(get("/api/messages/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));
    }

    @Test
    void inbox_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/messages/conversations"))
                .andExpect(status().isUnauthorized());
    }
}
