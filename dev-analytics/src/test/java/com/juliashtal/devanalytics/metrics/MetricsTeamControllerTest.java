package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.git.service.RepoService;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.security.TeamAccessGuard;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.TeamService;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link MetricsTeamController} — all team-scoped and member-scoped endpoints.
 */
@WebMvcTest(MetricsTeamController.class)
@AutoConfigureMockMvc(addFilters = false)
class MetricsTeamControllerTest {

    @Autowired MockMvc mvc;

    @MockBean MetricSnapshotService snapshotService;
    @MockBean MetricsService metricsService;
    @MockBean RepoService repoService;
    @MockBean TeamService teamService;
    @MockBean UserService userService;
    @MockBean CheckHelper checkHelper;
    @MockBean MetricSnapshotRepository snapshotRepository;
    @MockBean TeamAccessGuard teamAccessGuard;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;

    private static final LocalDate FROM = LocalDate.of(2024, 1, 1);
    private static final LocalDate TO   = LocalDate.of(2024, 1, 31);
    private static final LocalDate DAY  = LocalDate.of(2024, 1, 15);
    private static final Long TEAM_ID   = 7L;
    private static final Long MEMBER_ID = 2L;

    private User currentUser;

    @BeforeEach
    void stubUser() {
        currentUser = new User();
        currentUser.setId(1L);
        when(checkHelper.currentUser()).thenReturn(currentUser);
    }

    private MetricSnapshot teamSnapshot(User user, MetricType type, LocalDate date, double value) {
        MetricSnapshot s = new MetricSnapshot();
        s.setUser(user);
        s.setMetricType(type);
        s.setDate(date);
        s.setValue(value);
        return s;
    }

    private Team teamWithMember(User member) {
        Team team = new Team();
        team.setId(TEAM_ID);
        Set<User> members = new HashSet<>();
        members.add(member);
        team.setMembers(members);
        return team;
    }

    // =========================================================================
    // /calculate
    // =========================================================================

    @Test
    @WithMockUser(roles = "MANAGER")
    void calculateForTeam_invokesMetricsService() throws Exception {
        mvc.perform(post("/api/metrics/teams/{teamId}/calculate", TEAM_ID)
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk());

        verify(metricsService).calculateForTeam(TEAM_ID, currentUser.getId(), FROM, TO);
    }

    // =========================================================================
    // Team daily-series endpoints
    // =========================================================================

    @Test
    @WithMockUser(roles = "MANAGER")
    void getTeamDailyCommits_asManager_returnsPerMemberAndAggregate() throws Exception {
        User member = new User();
        member.setId(MEMBER_ID);
        member.setUsername("alice");
        Team team = teamWithMember(member);
        when(teamService.getById(TEAM_ID)).thenReturn(team);
        when(snapshotService.getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndDateBetween(
                eq(List.of(MEMBER_ID)), eq(TEAM_ID), eq(MetricType.DAILY_COMMITS_COUNT), eq(FROM), eq(TO)))
                .thenReturn(List.of(teamSnapshot(member, MetricType.DAILY_COMMITS_COUNT, DAY, 5)));

        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserRole).thenReturn(Role.MANAGER);

            mvc.perform(get("/api/metrics/teams/{teamId}/daily-commits-count", TEAM_ID)
                            .param("from", FROM.toString())
                            .param("to", TO.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].userId").value(MEMBER_ID))
                    .andExpect(jsonPath("$[0].value").value(5.0))
                    .andExpect(jsonPath("$[1].userId").doesNotExist())
                    .andExpect(jsonPath("$[1].username").value("team"))
                    .andExpect(jsonPath("$[1].value").value(5.0));
        }
    }

    @Test
    @WithMockUser
    void getTeamDailyCommits_asDeveloper_returnsAggregateOnly() throws Exception {
        User member = new User();
        member.setId(MEMBER_ID);
        member.setUsername("alice");
        Team team = teamWithMember(member);
        when(teamService.getById(TEAM_ID)).thenReturn(team);
        when(snapshotService.getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndDateBetween(
                eq(List.of(MEMBER_ID)), eq(TEAM_ID), eq(MetricType.DAILY_COMMITS_COUNT), eq(FROM), eq(TO)))
                .thenReturn(List.of(teamSnapshot(member, MetricType.DAILY_COMMITS_COUNT, DAY, 5)));

        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserRole).thenReturn(Role.DEVELOPER);

            mvc.perform(get("/api/metrics/teams/{teamId}/daily-commits-count", TEAM_ID)
                            .param("from", FROM.toString())
                            .param("to", TO.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].userId").doesNotExist())
                    .andExpect(jsonPath("$[0].username").value("team"));
        }
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getTeamDailyPrMerged_returnsSeries() throws Exception {
        User member = new User();
        member.setId(MEMBER_ID);
        member.setUsername("alice");
        Team team = teamWithMember(member);
        when(teamService.getById(TEAM_ID)).thenReturn(team);
        when(snapshotService.getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndDateBetween(
                eq(List.of(MEMBER_ID)), eq(TEAM_ID), eq(MetricType.DAILY_PR_MERGED), eq(FROM), eq(TO)))
                .thenReturn(List.of(teamSnapshot(member, MetricType.DAILY_PR_MERGED, DAY, 2)));

        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserRole).thenReturn(Role.MANAGER);

            mvc.perform(get("/api/metrics/teams/{teamId}/daily-pr-merged", TEAM_ID)
                            .param("from", FROM.toString())
                            .param("to", TO.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].value").value(2.0));
        }
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getTeamDailyIssuesClosed_returnsSeries() throws Exception {
        User member = new User();
        member.setId(MEMBER_ID);
        member.setUsername("alice");
        Team team = teamWithMember(member);
        when(teamService.getById(TEAM_ID)).thenReturn(team);
        when(snapshotService.getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndDateBetween(
                eq(List.of(MEMBER_ID)), eq(TEAM_ID), eq(MetricType.DAILY_ISSUES_CLOSED), eq(FROM), eq(TO)))
                .thenReturn(List.of(teamSnapshot(member, MetricType.DAILY_ISSUES_CLOSED, DAY, 1)));

        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserRole).thenReturn(Role.MANAGER);

            mvc.perform(get("/api/metrics/teams/{teamId}/daily-issues-closed", TEAM_ID)
                            .param("from", FROM.toString())
                            .param("to", TO.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].value").value(1.0));
        }
    }

    // =========================================================================
    // Team summary endpoints
    // =========================================================================

    @Test
    @WithMockUser(roles = "MANAGER")
    void getTeamSummary_returnsPerMemberTotals() throws Exception {
        User member = new User();
        member.setId(MEMBER_ID);
        member.setUsername("alice");
        member.setEmail("alice@example.com");
        Team team = teamWithMember(member);
        when(teamService.getById(TEAM_ID)).thenReturn(team);

        when(snapshotService.getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndDateBetween(
                anyList(), eq(TEAM_ID), any(MetricType.class), eq(FROM), eq(TO)))
                .thenReturn(List.of());
        when(snapshotService.getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndDateBetween(
                eq(List.of(MEMBER_ID)), eq(TEAM_ID), eq(MetricType.DAILY_COMMITS_COUNT), eq(FROM), eq(TO)))
                .thenReturn(List.of(teamSnapshot(member, MetricType.DAILY_COMMITS_COUNT, DAY, 9)));

        mvc.perform(get("/api/metrics/teams/{teamId}/summary", TEAM_ID)
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(MEMBER_ID))
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].metrics.DAILY_COMMITS_COUNT").value(9.0));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getTeamSummary_emptyTeam_returnsEmptyList() throws Exception {
        Team team = new Team();
        team.setId(TEAM_ID);
        team.setMembers(new HashSet<>());
        when(teamService.getById(TEAM_ID)).thenReturn(team);

        mvc.perform(get("/api/metrics/teams/{teamId}/summary", TEAM_ID)
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getMemberSummary_returnsMemberTotals() throws Exception {
        User member = new User();
        member.setId(MEMBER_ID);
        member.setUsername("alice");
        member.setEmail("alice@example.com");
        Team team = teamWithMember(member);
        when(teamService.getById(TEAM_ID)).thenReturn(team);
        when(userService.getById(MEMBER_ID)).thenReturn(member);

        when(snapshotService.getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(
                eq(member), eq(team), any(MetricType.class), eq(FROM), eq(TO)))
                .thenReturn(List.of());
        when(snapshotService.getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(
                eq(member), eq(team), eq(MetricType.DAILY_PR_CREATED), eq(FROM), eq(TO)))
                .thenReturn(List.of(teamSnapshot(member, MetricType.DAILY_PR_CREATED, DAY, 6)));

        mvc.perform(get("/api/metrics/teams/{teamId}/members/{memberId}/summary", TEAM_ID, MEMBER_ID)
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(MEMBER_ID))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.metrics.DAILY_PR_CREATED").value(6.0));
    }

    // =========================================================================
    // Member daily-series endpoints
    // =========================================================================

    @Test
    @WithMockUser(roles = "MANAGER")
    void getMemberDailyCommits_returnsSeries() throws Exception {
        User member = new User();
        member.setId(MEMBER_ID);
        member.setUsername("alice");
        Team team = teamWithMember(member);
        when(teamService.getById(TEAM_ID)).thenReturn(team);
        when(userService.getById(MEMBER_ID)).thenReturn(member);
        when(snapshotService.getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(
                eq(member), eq(team), eq(MetricType.DAILY_COMMITS_COUNT), eq(FROM), eq(TO)))
                .thenReturn(List.of(teamSnapshot(member, MetricType.DAILY_COMMITS_COUNT, DAY, 4)));

        mvc.perform(get("/api/metrics/teams/{teamId}/members/{memberId}/daily-commits-count", TEAM_ID, MEMBER_ID)
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value(4.0))
                .andExpect(jsonPath("$[0].metricType").value("DAILY_COMMITS_COUNT"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getMemberDailyPrCreated_returnsSeries() throws Exception {
        User member = new User();
        member.setId(MEMBER_ID);
        member.setUsername("alice");
        Team team = teamWithMember(member);
        when(teamService.getById(TEAM_ID)).thenReturn(team);
        when(userService.getById(MEMBER_ID)).thenReturn(member);
        when(snapshotService.getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(
                eq(member), eq(team), eq(MetricType.DAILY_PR_CREATED), eq(FROM), eq(TO)))
                .thenReturn(List.of(teamSnapshot(member, MetricType.DAILY_PR_CREATED, DAY, 2)));

        mvc.perform(get("/api/metrics/teams/{teamId}/members/{memberId}/daily-pr-created", TEAM_ID, MEMBER_ID)
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value(2.0))
                .andExpect(jsonPath("$[0].metricType").value("DAILY_PR_CREATED"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getMemberDailyChurn_returnsSeries() throws Exception {
        User member = new User();
        member.setId(MEMBER_ID);
        member.setUsername("alice");
        Team team = teamWithMember(member);
        when(teamService.getById(TEAM_ID)).thenReturn(team);
        when(userService.getById(MEMBER_ID)).thenReturn(member);
        when(snapshotService.getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(
                eq(member), eq(team), eq(MetricType.DAILY_CHURN_RATIO), eq(FROM), eq(TO)))
                .thenReturn(List.of(teamSnapshot(member, MetricType.DAILY_CHURN_RATIO, DAY, 0.25)));

        mvc.perform(get("/api/metrics/teams/{teamId}/members/{memberId}/daily-churn-ratio", TEAM_ID, MEMBER_ID)
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value(0.25))
                .andExpect(jsonPath("$[0].metricType").value("DAILY_CHURN_RATIO"));
    }
}
