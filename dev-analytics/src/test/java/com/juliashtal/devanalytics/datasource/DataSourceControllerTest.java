package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.config.SecurityConfig;
import com.juliashtal.devanalytics.datasource.controller.DataSourceController;
import com.juliashtal.devanalytics.datasource.model.SyncJobStatus;
import com.juliashtal.devanalytics.datasource.model.dto.SyncJobSummaryDto;
import com.juliashtal.devanalytics.datasource.service.AsyncDataSourceCollectService;
import com.juliashtal.devanalytics.datasource.service.DataSourceService;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.security.JwtAuthFilter;
import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller slice tests for the sync-history endpoint in {@link DataSourceController}.
 * {@link SecurityConfig} + {@link JwtAuthFilter} are imported so that the real security
 * filter chain enforces the 401 contract; authenticated tests use {@code @WithMockUser}
 * + {@code MockedStatic<SecurityUtils>} to short-circuit the CustomUserDetails cast.
 */
@WebMvcTest(DataSourceController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class DataSourceControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean DataSourceService dataSourceService;
    @MockBean AsyncDataSourceCollectService asyncCollectService;
    @MockBean SyncJobTracker syncJobTracker;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;
    // Required by ActivityInterceptor
    @MockBean UserService userService;

    private SyncJobSummaryDto completedJob() {
        return new SyncJobSummaryDto(
                10L, SyncJobStatus.COMPLETED, "commits",
                Instant.now().minusSeconds(300), Instant.now(),
                42, null);
    }

    @Test
    @WithMockUser
    void getSyncHistory_owner_returns200WithJobList() throws Exception {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            when(dataSourceService.getSyncHistory(5L, 5, 1L)).thenReturn(List.of(completedJob()));

            mockMvc.perform(get("/api/datasources/5/sync-history").param("limit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(10))
                    .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                    .andExpect(jsonPath("$[0].totalProcessed").value(42));
        }
    }

    @Test
    @WithMockUser
    void getSyncHistory_noJobs_returns200EmptyList() throws Exception {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            when(dataSourceService.getSyncHistory(5L, 5, 1L)).thenReturn(List.of());

            mockMvc.perform(get("/api/datasources/5/sync-history").param("limit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }
    }

    @Test
    @WithMockUser
    void getSyncHistory_nonOwner_returns404() throws Exception {
        // getForUser throws NoSuchElementException (security-through-obscurity) → 404
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(99L);
            when(dataSourceService.getSyncHistory(5L, 5, 99L))
                    .thenThrow(new NoSuchElementException("DataSource not found: 5"));

            mockMvc.perform(get("/api/datasources/5/sync-history").param("limit", "5"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void getSyncHistory_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/datasources/5/sync-history").param("limit", "5"))
                .andExpect(status().isUnauthorized());
    }
}
