package com.juliashtal.devanalytics.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.ai.controller.GoalController;
import com.juliashtal.devanalytics.ai.model.GoalDto;
import com.juliashtal.devanalytics.ai.model.GoalRequestDto;
import com.juliashtal.devanalytics.ai.service.GoalService;
import com.juliashtal.devanalytics.config.SecurityConfig;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller slice tests for {@link GoalController}.
 * {@link SecurityConfig} is imported so that CSRF is disabled (matching production) and
 * the correct 401/403 contract is enforced by the real security filter chain.
 * {@link JwtAuthFilter} is imported so filters are active; {@code @WithMockUser} sets the
 * security context directly, letting unauthenticated tests verify the 401 response.
 */
@WebMvcTest(GoalController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class GoalControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean GoalService goalService;
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
    void createGoal_validRequest_returns201() throws Exception {
        GoalRequestDto req = new GoalRequestDto("DAILY_COMMITS_COUNT", 5.0, LocalDate.of(2026, 7, 31));
        GoalDto dto = new GoalDto(1L, "DAILY_COMMITS_COUNT", 5.0, LocalDate.of(2026, 7, 31), Instant.now());
        when(goalService.createGoal(any(), any())).thenReturn(dto);

        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.metricType").value("DAILY_COMMITS_COUNT"));
    }

    @Test
    @WithMockUser
    void getGoals_authenticated_returns200() throws Exception {
        when(goalService.getGoals(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/goals"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @WithMockUser
    void deleteGoal_authenticated_returns204() throws Exception {
        doNothing().when(goalService).deleteGoal(any(), eq(5L));

        mockMvc.perform(delete("/api/goals/5"))
                .andExpect(status().isNoContent());
    }

    @Test
    void createGoal_unauthenticated_returns401() throws Exception {
        GoalRequestDto req = new GoalRequestDto("DAILY_COMMITS_COUNT", 5.0, LocalDate.of(2026, 7, 31));

        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getGoals_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/goals"))
                .andExpect(status().isUnauthorized());
    }
}
