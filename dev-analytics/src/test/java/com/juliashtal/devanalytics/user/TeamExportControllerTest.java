package com.juliashtal.devanalytics.user;

import com.juliashtal.devanalytics.config.SecurityConfig;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.security.JwtAuthFilter;
import com.juliashtal.devanalytics.security.model.CustomUserDetails;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.controller.TeamExportController;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.TeamService;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice tests for {@link TeamExportController}.
 *
 * <p>The MANAGER/ADMIN gate is only half the rule: a manager of some other team carries the right
 * role and must still be refused, which is checked in the controller body rather than by an
 * annotation. Both halves are pinned, the first through the real filter chain.</p>
 */
@WebMvcTest(TeamExportController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class TeamExportControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean TeamService teamService;
    @MockBean MetricSnapshotService metricSnapshotService;
    // Both a controller collaborator and the bean ActivityInterceptor needs
    @MockBean UserService userService;
    // Required by SecurityConfig / JwtAuthFilter when filters are active
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;

    private static User member(long id, String username, Role role) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setRole(role);
        return u;
    }

    private static CustomUserDetails principal(User u) {
        return new CustomUserDetails(u);
    }

    private static Team team(long id, String name, User manager, User... members) {
        Team t = new Team();
        t.setId(id);
        t.setName(name);
        t.setManager(manager);
        t.setMembers(Set.of(members));
        return t;
    }

    private static MetricSnapshot snapshot(double value) {
        MetricSnapshot s = new MetricSnapshot();
        s.setValue(value);
        return s;
    }

    @Test
    void exportTeamCsv_asTeamManager_returnsCsvWithHeaderRow() throws Exception {
        User manager = member(7L, "manager", Role.MANAGER);
        User dev = member(8L, "alice", Role.DEVELOPER);
        when(teamService.getById(5L)).thenReturn(team(5L, "Platform", manager, dev));
        when(userService.getById(7L)).thenReturn(manager);
        when(metricSnapshotService.getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(
                any(), any(), eq(MetricType.DAILY_COMMITS_COUNT), any(), any()))
                .thenReturn(List.of(snapshot(3.0), snapshot(4.0)));

        String csv = mockMvc.perform(get("/api/teams/5/export")
                        .with(user(principal(manager)))
                        .param("from", "2026-09-01").param("to", "2026-09-07"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).startsWith("username,metric,value,unit,period_from,period_to\n");
        // Snapshots are summed over the window, not listed one row per day.
        assertThat(csv).contains("\"alice\",DAILY_COMMITS_COUNT,7.0000,commits,2026-09-01,2026-09-07");
    }

    @Test
    void exportTeamCsv_teamNameWithSlashes_isSanitisedInTheFilename() throws Exception {
        User manager = member(7L, "manager", Role.MANAGER);
        when(teamService.getById(5L)).thenReturn(team(5L, "Platform/Core Team", manager));
        when(userService.getById(7L)).thenReturn(manager);

        mockMvc.perform(get("/api/teams/5/export")
                        .with(user(principal(manager)))
                        .param("from", "2026-09-01").param("to", "2026-09-07"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"team-Platform_Core_Team-2026-09-01-2026-09-07.csv\""));
    }

    @Test
    void exportTeamCsv_asAdminOfAnotherTeam_returns200() throws Exception {
        User manager = member(7L, "manager", Role.MANAGER);
        User admin = member(1L, "root", Role.ADMIN);
        when(teamService.getById(5L)).thenReturn(team(5L, "Platform", manager));
        when(userService.getById(1L)).thenReturn(admin);

        mockMvc.perform(get("/api/teams/5/export")
                        .with(user(principal(admin)))
                        .param("from", "2026-09-01").param("to", "2026-09-07"))
                .andExpect(status().isOk());
    }

    @Test
    void exportTeamCsv_asManagerOfAnotherTeam_returns403() throws Exception {
        User owner = member(7L, "owner", Role.MANAGER);
        User outsider = member(8L, "outsider", Role.MANAGER);
        when(teamService.getById(5L)).thenReturn(team(5L, "Platform", owner));
        when(userService.getById(8L)).thenReturn(outsider);

        mockMvc.perform(get("/api/teams/5/export")
                        .with(user(principal(outsider)))
                        .param("from", "2026-09-01").param("to", "2026-09-07"))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportTeamCsv_asDeveloper_returns403AtTheFilterChain() throws Exception {
        User developer = member(8L, "alice", Role.DEVELOPER);

        mockMvc.perform(get("/api/teams/5/export")
                        .with(user(principal(developer)))
                        .param("from", "2026-09-01").param("to", "2026-09-07"))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportTeamCsv_missingDateRange_returns400() throws Exception {
        User manager = member(7L, "manager", Role.MANAGER);

        mockMvc.perform(get("/api/teams/5/export").with(user(principal(manager))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportTeamCsv_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/teams/5/export")
                        .param("from", "2026-09-01").param("to", "2026-09-07"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exportTeamCsv_dateRangeIsPassedThroughUnchanged() throws Exception {
        User manager = member(7L, "manager", Role.MANAGER);
        User dev = member(8L, "alice", Role.DEVELOPER);
        when(teamService.getById(5L)).thenReturn(team(5L, "Platform", manager, dev));
        when(userService.getById(7L)).thenReturn(manager);

        mockMvc.perform(get("/api/teams/5/export")
                        .with(user(principal(manager)))
                        .param("from", "2026-09-01").param("to", "2026-09-07"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(metricSnapshotService, org.mockito.Mockito.atLeastOnce())
                .getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(
                        eq(dev), any(), any(),
                        eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 7)));
    }
}
