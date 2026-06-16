package com.juliashtal.devanalytics.ai;

import com.juliashtal.devanalytics.ai.controller.AiSummaryController;
import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.ai.service.MetricSummaryPersistenceService;
import com.juliashtal.devanalytics.ai.service.MetricsAiService;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.TeamService;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AiSummaryController.class)
@AutoConfigureMockMvc(addFilters = false)
class AiSummaryControllerTest {

    @Autowired MockMvc mvc;
    @MockBean MetricsAiService metricsAiService;
    @MockBean MetricSummaryPersistenceService persistenceService;
    @MockBean TeamService teamService;
    @MockBean CheckHelper checkHelper;
    @MockBean UserService userService;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;

    private static final Long TEAM_ID = 1L;
    private static final Long MEMBER_ID = 2L;

    private MetricsSummaryDto stubbedSummary() {
        return MetricsSummaryDto.builder()
                .from(LocalDate.of(2024, 1, 1))
                .to(LocalDate.of(2024, 1, 31))
                .scope("PERSONAL")
                .headline("Consistent delivery week")
                .overview("Member maintained a steady commit cadence.")
                .insights(List.of())
                .recommendations(List.of())
                .rawModelOutput("{}")
                .modelName("llama3.2")
                .build();
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getMemberSummary_asManager_succeeds() throws Exception {
        User manager = new User();
        manager.setId(10L);
        when(checkHelper.currentUser()).thenReturn(manager);
        when(metricsAiService.generateMemberSummary(any(), eq(TEAM_ID), eq(MEMBER_ID), any(), any()))
                .thenReturn(stubbedSummary());

        mvc.perform(get("/api/ai/summary/teams/{teamId}/member/{memberId}", TEAM_ID, MEMBER_ID)
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Consistent delivery week"))
                .andExpect(jsonPath("$.scope").value("PERSONAL"));
    }

    @Test
    @WithMockUser
    void getMemberSummary_asNonManager_returns403() throws Exception {
        User nonManager = new User();
        nonManager.setId(99L);
        when(checkHelper.currentUser()).thenReturn(nonManager);
        when(metricsAiService.generateMemberSummary(any(), eq(TEAM_ID), eq(MEMBER_ID), any(), any()))
                .thenThrow(new ForbiddenException("Only the team manager or an admin can generate member AI summaries"));

        mvc.perform(get("/api/ai/summary/teams/{teamId}/member/{memberId}", TEAM_ID, MEMBER_ID)
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void getLatestPersonalSummary_whenExists_returns200() throws Exception {
        User user = new User();
        user.setId(5L);
        when(checkHelper.currentUser()).thenReturn(user);
        when(persistenceService.findLatestPersonal(user)).thenReturn(Optional.of(stubbedSummary()));

        mvc.perform(get("/api/ai/summary/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Consistent delivery week"));
    }

    @Test
    @WithMockUser
    void getLatestPersonalSummary_whenNone_returns204() throws Exception {
        User user = new User();
        user.setId(5L);
        when(checkHelper.currentUser()).thenReturn(user);
        when(persistenceService.findLatestPersonal(user)).thenReturn(Optional.empty());

        mvc.perform(get("/api/ai/summary/latest"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getLatestTeamSummary_whenExists_returns200() throws Exception {
        Team team = new Team();
        team.setId(TEAM_ID);
        when(teamService.getById(TEAM_ID)).thenReturn(team);
        when(persistenceService.findLatestTeam(team)).thenReturn(Optional.of(stubbedSummary()));

        mvc.perform(get("/api/ai/summary/teams/{teamId}/latest", TEAM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Consistent delivery week"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getLatestTeamSummary_whenNone_returns204() throws Exception {
        Team team = new Team();
        team.setId(TEAM_ID);
        when(teamService.getById(TEAM_ID)).thenReturn(team);
        when(persistenceService.findLatestTeam(team)).thenReturn(Optional.empty());

        mvc.perform(get("/api/ai/summary/teams/{teamId}/latest", TEAM_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void getPersonalSummary_withoutRepoId_returnsSummary() throws Exception {
        User user = new User();
        user.setId(5L);
        when(checkHelper.currentUser()).thenReturn(user);
        when(metricsAiService.generateSummary(eq(user), eq(LocalDate.of(2024, 1, 1)), eq(LocalDate.of(2024, 1, 31)), isNull()))
                .thenReturn(stubbedSummary());

        mvc.perform(get("/api/ai/summary")
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Consistent delivery week"))
                .andExpect(jsonPath("$.scope").value("PERSONAL"));
    }

    @Test
    @WithMockUser
    void getPersonalSummary_withRepoId_passesRepoIdToService() throws Exception {
        User user = new User();
        user.setId(5L);
        when(checkHelper.currentUser()).thenReturn(user);
        when(metricsAiService.generateSummary(eq(user), eq(LocalDate.of(2024, 1, 1)), eq(LocalDate.of(2024, 1, 31)), eq(7L)))
                .thenReturn(stubbedSummary());

        mvc.perform(get("/api/ai/summary")
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31")
                        .param("repoId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Consistent delivery week"));

        verify(metricsAiService).generateSummary(eq(user), eq(LocalDate.of(2024, 1, 1)), eq(LocalDate.of(2024, 1, 31)), eq(7L));
    }

    @Test
    @WithMockUser
    void getRepoSummary_returnsRepositoryScopedSummary() throws Exception {
        User user = new User();
        user.setId(5L);
        when(checkHelper.currentUser()).thenReturn(user);
        when(metricsAiService.generateSummary(eq(user), eq(LocalDate.of(2024, 1, 1)), eq(LocalDate.of(2024, 1, 31)), eq(3L)))
                .thenReturn(stubbedSummary());

        mvc.perform(get("/api/ai/summary/repos/{repoId}", 3L)
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Consistent delivery week"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getTeamSummary_asManager_returnsSummary() throws Exception {
        User manager = new User();
        manager.setId(10L);
        when(checkHelper.currentUser()).thenReturn(manager);
        when(metricsAiService.generateTeamSummary(eq(manager), eq(TEAM_ID), eq(LocalDate.of(2024, 1, 1)), eq(LocalDate.of(2024, 1, 31))))
                .thenReturn(stubbedSummary());

        mvc.perform(get("/api/ai/summary/teams/{teamId}", TEAM_ID)
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Consistent delivery week"));
    }

    @Test
    @WithMockUser
    void getTeamSummary_asNonManager_returns403() throws Exception {
        User nonManager = new User();
        nonManager.setId(99L);
        when(checkHelper.currentUser()).thenReturn(nonManager);
        when(metricsAiService.generateTeamSummary(eq(nonManager), eq(TEAM_ID), any(), any()))
                .thenThrow(new ForbiddenException("Only the team manager or an admin can generate team AI summaries"));

        mvc.perform(get("/api/ai/summary/teams/{teamId}", TEAM_ID)
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void getPersonalHistory_defaultLimit_returnsHistoryList() throws Exception {
        User user = new User();
        user.setId(5L);
        when(checkHelper.currentUser()).thenReturn(user);
        when(persistenceService.findHistoryPersonal(user, 10)).thenReturn(List.of(stubbedSummary()));

        mvc.perform(get("/api/ai/summary/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].headline").value("Consistent delivery week"));
    }

    @Test
    @WithMockUser
    void getPersonalHistory_limitAboveMax_clampedTo50() throws Exception {
        User user = new User();
        user.setId(5L);
        when(checkHelper.currentUser()).thenReturn(user);
        when(persistenceService.findHistoryPersonal(user, 50)).thenReturn(List.of(stubbedSummary()));

        mvc.perform(get("/api/ai/summary/history").param("limit", "100"))
                .andExpect(status().isOk());

        verify(persistenceService).findHistoryPersonal(user, 50);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getTeamHistory_asManager_returnsHistoryList() throws Exception {
        Team team = new Team();
        team.setId(TEAM_ID);
        when(teamService.getById(TEAM_ID)).thenReturn(team);
        when(persistenceService.findHistoryTeam(team, 10)).thenReturn(List.of(stubbedSummary()));

        mvc.perform(get("/api/ai/summary/teams/{teamId}/history", TEAM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].headline").value("Consistent delivery week"));
    }
}
