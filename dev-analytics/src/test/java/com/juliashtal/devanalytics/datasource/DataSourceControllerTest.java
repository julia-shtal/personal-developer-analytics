package com.juliashtal.devanalytics.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.config.SecurityConfig;
import com.juliashtal.devanalytics.datasource.controller.DataSourceController;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.model.SyncJobEntity;
import com.juliashtal.devanalytics.datasource.model.SyncJobStatus;
import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;
import com.juliashtal.devanalytics.datasource.model.dto.DataSourceResponseDto;
import com.juliashtal.devanalytics.datasource.model.dto.SyncJobSummaryDto;
import com.juliashtal.devanalytics.datasource.model.dto.UpdateDataSourceRequest;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller slice tests for {@link DataSourceController}: CRUD, collection triggering, and
 * the status/history reads. {@link SecurityConfig} + {@link JwtAuthFilter} are imported so that
 * the real security filter chain enforces the 401 contract; authenticated tests use
 * {@code @WithMockUser} + {@code MockedStatic<SecurityUtils>} to short-circuit the
 * CustomUserDetails cast.
 */
@WebMvcTest(DataSourceController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class DataSourceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

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

    private static DataSourceResponseDto responseDto(long id) {
        return new DataSourceResponseDto(id, DataSourceType.GITHUB, "GitHub personal",
                "https://github.com", null, true, null, Instant.now(), null, true, 3L);
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

    @Test
    @WithMockUser
    void create_valid_returns201WithLocationAndBody() throws Exception {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);

            CreateDataSourceRequest req = new CreateDataSourceRequest();
            req.setType(DataSourceType.GITHUB);
            req.setName("GitHub personal");
            when(dataSourceService.create(eq(1L), any())).thenReturn(responseDto(5L));

            mockMvc.perform(post("/api/datasources")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/datasources/5"))
                    .andExpect(jsonPath("$.id").value(5))
                    .andExpect(jsonPath("$.name").value("GitHub personal"));
        }
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        CreateDataSourceRequest req = new CreateDataSourceRequest();
        req.setType(DataSourceType.GITHUB);
        req.setName("GitHub personal");

        mockMvc.perform(post("/api/datasources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void create_missingRequiredFields_returns400() throws Exception {
        CreateDataSourceRequest req = new CreateDataSourceRequest(); // type and name both @NotNull/@NotBlank

        mockMvc.perform(post("/api/datasources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void list_returnsCallersDataSources() throws Exception {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            when(dataSourceService.listForUser(1L)).thenReturn(List.of(responseDto(5L)));

            mockMvc.perform(get("/api/datasources"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(5));
        }
    }

    @Test
    @WithMockUser
    void get_owner_returns200WithConfig() throws Exception {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            DataSourceConfig cfg = new DataSourceConfig();
            cfg.setId(5L);
            cfg.setType(DataSourceType.GITHUB);
            cfg.setName("GitHub personal");
            when(dataSourceService.getForUser(1L, 5L)).thenReturn(cfg);

            mockMvc.perform(get("/api/datasources/5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(5))
                    .andExpect(jsonPath("$.name").value("GitHub personal"));
        }
    }

    @Test
    @WithMockUser
    void update_owner_returns200WithUpdatedConfig() throws Exception {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            UpdateDataSourceRequest req = new UpdateDataSourceRequest();
            req.setName("Renamed");
            DataSourceConfig updated = new DataSourceConfig();
            updated.setId(5L);
            updated.setName("Renamed");
            when(dataSourceService.update(eq(1L), eq(5L), any())).thenReturn(updated);

            mockMvc.perform(put("/api/datasources/5")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Renamed"));
        }
    }

    @Test
    @WithMockUser
    void delete_owner_returns204() throws Exception {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);

            mockMvc.perform(delete("/api/datasources/5"))
                    .andExpect(status().isNoContent());

            verify(dataSourceService).delete(1L, 5L);
        }
    }

    @Test
    @WithMockUser
    void collect_owner_returns202AndTriggersAsyncCollection() throws Exception {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            DataSourceConfig cfg = new DataSourceConfig();
            cfg.setId(5L);
            when(dataSourceService.getForUser(1L, 5L)).thenReturn(cfg);

            mockMvc.perform(post("/api/datasources/5/collect"))
                    .andExpect(status().isAccepted());

            verify(asyncCollectService).collectAsync(1L, 5L);
        }
    }

    @Test
    @WithMockUser
    void collectStatus_inMemoryRunning_returns200WithLiveState() throws Exception {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            DataSourceConfig cfg = new DataSourceConfig();
            cfg.setId(5L);
            when(dataSourceService.getForUser(1L, 5L)).thenReturn(cfg);

            SyncJobTracker.JobState state = new SyncJobTracker.JobState();
            state.phase = "commits";
            state.phaseNumber = 1;
            state.totalPhases = 3;
            state.phaseTotal = 100;
            state.phaseProcessed.set(10);
            state.totalProcessed.set(10);
            when(syncJobTracker.getState(5L)).thenReturn(Optional.of(state));
            when(syncJobTracker.phaseEtaSeconds(state)).thenReturn(30L);
            when(syncJobTracker.overallEtaSeconds(state)).thenReturn(90L);

            mockMvc.perform(get("/api/datasources/5/collect/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.running").value(true))
                    .andExpect(jsonPath("$.phase").value("commits"))
                    .andExpect(jsonPath("$.phaseEtaSeconds").value(30))
                    .andExpect(jsonPath("$.overallEtaSeconds").value(90));
        }
    }

    @Test
    @WithMockUser
    void collectStatus_noInMemoryButPersistedCompleted_returns200FromEntity() throws Exception {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            DataSourceConfig cfg = new DataSourceConfig();
            cfg.setId(5L);
            when(dataSourceService.getForUser(1L, 5L)).thenReturn(cfg);
            when(syncJobTracker.getState(5L)).thenReturn(Optional.empty());

            SyncJobEntity entity = new SyncJobEntity();
            entity.setStatus(SyncJobStatus.COMPLETED);
            entity.setStartedAt(Instant.now().minusSeconds(60));
            entity.setCompletedAt(Instant.now());
            entity.setTotalProcessed(200);
            entity.setResult("Synced 200 items");
            when(syncJobTracker.findLatestPersisted(5L)).thenReturn(Optional.of(entity));

            mockMvc.perform(get("/api/datasources/5/collect/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.running").value(false))
                    .andExpect(jsonPath("$.result").value("Synced 200 items"))
                    .andExpect(jsonPath("$.totalProcessed").value(200));
        }
    }

    @Test
    @WithMockUser
    void collectStatus_persistedInterrupted_replacesResultWithInterruptedMessage() throws Exception {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            DataSourceConfig cfg = new DataSourceConfig();
            cfg.setId(5L);
            when(dataSourceService.getForUser(1L, 5L)).thenReturn(cfg);
            when(syncJobTracker.getState(5L)).thenReturn(Optional.empty());

            SyncJobEntity entity = new SyncJobEntity();
            entity.setStatus(SyncJobStatus.INTERRUPTED);
            entity.setStartedAt(Instant.now().minusSeconds(60));
            entity.setResult("this should be overridden");
            when(syncJobTracker.findLatestPersisted(5L)).thenReturn(Optional.of(entity));

            mockMvc.perform(get("/api/datasources/5/collect/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("Job was interrupted by a server restart"));
        }
    }

    @Test
    @WithMockUser
    void collectStatus_persistedFailed_includesErrorAndNullResult() throws Exception {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            DataSourceConfig cfg = new DataSourceConfig();
            cfg.setId(5L);
            when(dataSourceService.getForUser(1L, 5L)).thenReturn(cfg);
            when(syncJobTracker.getState(5L)).thenReturn(Optional.empty());

            SyncJobEntity entity = new SyncJobEntity();
            entity.setStatus(SyncJobStatus.FAILED);
            entity.setStartedAt(Instant.now().minusSeconds(60));
            entity.setCompletedAt(Instant.now());
            entity.setError("GitHub API rate limit exceeded");
            when(syncJobTracker.findLatestPersisted(5L)).thenReturn(Optional.of(entity));

            mockMvc.perform(get("/api/datasources/5/collect/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").doesNotExist())
                    .andExpect(jsonPath("$.error").value("GitHub API rate limit exceeded"));
        }
    }

    @Test
    @WithMockUser
    void collectStatus_neitherInMemoryNorPersisted_returns404() throws Exception {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            DataSourceConfig cfg = new DataSourceConfig();
            cfg.setId(5L);
            when(dataSourceService.getForUser(1L, 5L)).thenReturn(cfg);
            when(syncJobTracker.getState(5L)).thenReturn(Optional.empty());
            when(syncJobTracker.findLatestPersisted(5L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/datasources/5/collect/status"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    @WithMockUser
    void activeCollectStatuses_runningAndRecentlyDoneIncluded_staleAndUntrackedExcluded() throws Exception {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            when(dataSourceService.listForUser(1L)).thenReturn(
                    List.of(responseDto(1L), responseDto(2L), responseDto(3L), responseDto(4L)));

            SyncJobTracker.JobState running = new SyncJobTracker.JobState();
            running.running = true;

            SyncJobTracker.JobState recentlyDone = new SyncJobTracker.JobState();
            recentlyDone.running = false;
            recentlyDone.completedAt = Instant.now().minusSeconds(60);

            SyncJobTracker.JobState staleDone = new SyncJobTracker.JobState();
            staleDone.running = false;
            staleDone.completedAt = Instant.now().minusSeconds(600);

            when(syncJobTracker.getState(1L)).thenReturn(Optional.of(running));
            when(syncJobTracker.getState(2L)).thenReturn(Optional.of(recentlyDone));
            when(syncJobTracker.getState(3L)).thenReturn(Optional.of(staleDone));
            when(syncJobTracker.getState(4L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/datasources/collect/status/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.['1']").exists())
                    .andExpect(jsonPath("$.['2']").exists())
                    .andExpect(jsonPath("$.['3']").doesNotExist())
                    .andExpect(jsonPath("$.['4']").doesNotExist());
        }
    }
}
