package com.juliashtal.devanalytics.attribution;

import com.juliashtal.devanalytics.attribution.controller.AttributionMigrationController;
import com.juliashtal.devanalytics.config.SecurityConfig;
import com.juliashtal.devanalytics.exception.ConflictException;
import com.juliashtal.devanalytics.security.JwtAuthFilter;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice tests for {@link AttributionMigrationController}.
 *
 * <p>The migration spends a GitHub API call per page of every repository's history, so the
 * ADMIN gate and the 202-not-200 start contract are the properties worth pinning.</p>
 */
@WebMvcTest(AttributionMigrationController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class AttributionMigrationControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean AttributionMigrationService migrationService;
    // Required by SecurityConfig / JwtAuthFilter when filters are active
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;
    // Required by ActivityInterceptor (HandlerInterceptor picked up by @WebMvcTest)
    @MockBean UserService userService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void start_asAdmin_returns202AndDoesNotBlock() throws Exception {
        mockMvc.perform(post("/api/admin/attribution/migrate"))
                .andExpect(status().isAccepted());

        verify(migrationService).start();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void start_alreadyRunning_returns409() throws Exception {
        doThrow(new ConflictException("Migration already running")).when(migrationService).start();

        mockMvc.perform(post("/api/admin/attribution/migrate"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void status_asAdmin_returnsProgressCounters() throws Exception {
        when(migrationService.status())
                .thenReturn(new AttributionMigrationStatus(2L, 5L, 11L, true));

        mockMvc.perform(get("/api/admin/attribution/migrate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usersWithoutGithubId").value(2))
                .andExpect(jsonPath("$.pendingRepos").value(5))
                .andExpect(jsonPath("$.doneRepos").value(11))
                .andExpect(jsonPath("$.running").value(true));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void start_asManager_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/attribution/migrate"))
                .andExpect(status().isForbidden());
    }

    @Test
    void status_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/attribution/migrate"))
                .andExpect(status().isUnauthorized());
    }
}
