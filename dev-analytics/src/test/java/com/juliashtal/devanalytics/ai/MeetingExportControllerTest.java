package com.juliashtal.devanalytics.ai;

import com.juliashtal.devanalytics.ai.controller.MeetingExportController;
import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.ai.service.MeetingExportService;
import com.juliashtal.devanalytics.ai.service.MetricsAiService;
import com.juliashtal.devanalytics.config.SecurityConfig;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.metrics.service.MetricsAnomalyService;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.security.JwtAuthFilter;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.TeamService;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller slice tests for {@link MeetingExportController}.
 * {@link SecurityConfig} is imported so that path-level RBAC rules
 * ({@code /api/teams/**} restricted to MANAGER/ADMIN) and the 401/403
 * contract are verified against the real security filter chain.
 * {@link JwtAuthFilter} is mocked so that {@code @WithMockUser} can set
 * the security context directly without a real JWT.
 */
@WebMvcTest(MeetingExportController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class MeetingExportControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean MetricsAiService metricsAiService;
    @MockBean MeetingExportService meetingExportService;
    @MockBean MetricsAnomalyService metricsAnomalyService;
    @MockBean MetricSnapshotService metricSnapshotService;
    @MockBean TeamService teamService;
    @MockBean UserService userService;
    @MockBean CheckHelper checkHelper;
    // Required by SecurityConfig / JwtAuthFilter
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;

    private User manager;
    private User member;
    private Team team;

    @BeforeEach
    void setUp() {
        manager = new User();
        manager.setId(10L);
        manager.setUsername("manager");
        manager.setRole(Role.MANAGER);

        member = new User();
        member.setId(20L);
        member.setUsername("alice");
        member.setEmail("alice@example.com");
        member.setLastActiveAt(Instant.now());

        team = new Team();
        team.setId(1L);
        team.setName("backend");

        when(checkHelper.currentUser()).thenReturn(manager);
        when(teamService.getById(1L)).thenReturn(team);
        when(userService.getById(20L)).thenReturn(member);
        when(metricSnapshotService.getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(
                any(), any(), any(), any(), any())).thenReturn(List.of());
        when(metricsAnomalyService.computeAnomalies(any(), any(), any())).thenReturn(Map.of());
        when(metricsAiService.generateMemberSummary(any(), anyLong(), anyLong(), any(), any()))
                .thenReturn(MetricsSummaryDto.builder()
                        .headline("Test headline")
                        .overview("Overview text")
                        .insights(List.of())
                        .recommendations(List.of())
                        .modelName("llama3.2")
                        .build());
        when(meetingExportService.buildMarkdown(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("# 1:1 Meeting Prep — alice\n**Period:** 2026-06-01 – 2026-06-30\n");
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void exportMeetingPrep_managerRole_returns200WithMarkdown() throws Exception {
        mockMvc.perform(get("/api/teams/1/members/20/export")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/markdown"))
                .andExpect(header().string("Content-Disposition",
                        startsWith("attachment; filename=\"1on1-alice")))
                .andExpect(content().string(containsString("alice")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void exportMeetingPrep_adminRole_returns200() throws Exception {
        mockMvc.perform(get("/api/teams/1/members/20/export")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void exportMeetingPrep_developerRole_returns403() throws Exception {
        mockMvc.perform(get("/api/teams/1/members/20/export")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportMeetingPrep_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/teams/1/members/20/export")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isForbidden());
    }
}
