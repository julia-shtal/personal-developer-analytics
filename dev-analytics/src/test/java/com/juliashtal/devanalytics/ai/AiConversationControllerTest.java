package com.juliashtal.devanalytics.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.ai.controller.AiConversationController;
import com.juliashtal.devanalytics.ai.model.ConversationDto;
import com.juliashtal.devanalytics.ai.model.MessageDto;
import com.juliashtal.devanalytics.ai.model.SendMessageRequest;
import com.juliashtal.devanalytics.ai.model.StartConversationRequest;
import com.juliashtal.devanalytics.ai.service.AiConversationService;
import com.juliashtal.devanalytics.config.SecurityConfig;
import com.juliashtal.devanalytics.exception.ForbiddenException;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice tests for {@link AiConversationController}.
 *
 * <p>Conversations belong to one user, so every route resolves the caller through
 * {@link CheckHelper} rather than a request field — these pin that the resolved user is what
 * reaches the service.</p>
 */
@WebMvcTest(AiConversationController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class AiConversationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AiConversationService conversationService;
    @MockBean CheckHelper checkHelper;
    // Required by SecurityConfig / JwtAuthFilter when filters are active
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;
    // Required by ActivityInterceptor (HandlerInterceptor picked up by @WebMvcTest)
    @MockBean UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(checkHelper.currentUser()).thenReturn(user);
    }

    @Test
    @WithMockUser
    void startConversation_validRequest_returns201() throws Exception {
        StartConversationRequest req = new StartConversationRequest();
        req.setSummaryScope("PERSONAL");
        req.setSummaryJson("{\"headline\":\"steady week\"}");
        when(conversationService.startConversation(any(), any(), any()))
                .thenReturn(ConversationDto.builder()
                        .id(4L).createdAt(Instant.parse("2026-09-01T10:00:00Z")).build());

        mockMvc.perform(post("/api/ai/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4));

        verify(conversationService).startConversation(user, "PERSONAL", "{\"headline\":\"steady week\"}");
    }

    @Test
    @WithMockUser
    void sendMessage_validRequest_returns200WithAssistantReply() throws Exception {
        SendMessageRequest req = new SendMessageRequest();
        req.setContent("why did my churn rise?");
        when(conversationService.sendMessage(any(), eq(4L), any()))
                .thenReturn(MessageDto.builder()
                        .id(9L).role("assistant").content("Larger refactors landed midweek.")
                        .createdAt(Instant.parse("2026-09-01T10:01:00Z")).build());

        mockMvc.perform(post("/api/ai/conversations/4/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("assistant"))
                .andExpect(jsonPath("$.content").value("Larger refactors landed midweek."));

        verify(conversationService).sendMessage(user, 4L, "why did my churn rise?");
    }

    @Test
    @WithMockUser
    void getMessages_ownConversation_returns200() throws Exception {
        when(conversationService.getMessages(user, 4L)).thenReturn(List.of(
                MessageDto.builder().id(1L).role("user").content("hi")
                        .createdAt(Instant.parse("2026-09-01T10:00:00Z")).build()));

        mockMvc.perform(get("/api/ai/conversations/4/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("user"));
    }

    @Test
    @WithMockUser
    void getMessages_anotherUsersConversation_returns403() throws Exception {
        when(conversationService.getMessages(any(), eq(99L)))
                .thenThrow(new ForbiddenException("Conversation does not belong to the current user"));

        mockMvc.perform(get("/api/ai/conversations/99/messages"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMessages_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/ai/conversations/4/messages"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startConversation_unauthenticated_returns401() throws Exception {
        StartConversationRequest req = new StartConversationRequest();
        req.setSummaryScope("PERSONAL");

        mockMvc.perform(post("/api/ai/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }
}
