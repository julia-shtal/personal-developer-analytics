package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.metrics.service.MetricsAnomalyService;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.security.TeamAccessGuard;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.git.service.RepoService;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for the remaining {@link MetricsController} endpoints not already covered by
 * {@link MetricsAggregationTest} (which exercises the cross-repo aggregation formulas for
 * /daily-commits-count, /daily-churn-ratio and /pr-lead-time).
 */
@WebMvcTest(MetricsController.class)
@AutoConfigureMockMvc(addFilters = false)
class MetricsControllerTest {

    @Autowired MockMvc mvc;

    @MockBean MetricSnapshotService snapshotService;
    @MockBean MetricsService metricsService;
    @MockBean MetricsAnomalyService anomalyService;
    @MockBean RepoService repoService;
    @MockBean TeamService teamService;
    @MockBean UserService userService;
    @MockBean CheckHelper checkHelper;
    @MockBean MetricSnapshotRepository snapshotRepository;
    @MockBean TeamAccessGuard teamAccessGuard;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;

    private static final LocalDate FROM = LocalDate.of(2024, 1, 1);
    private static final LocalDate TO = LocalDate.of(2024, 1, 31);
    private static final LocalDate DAY = LocalDate.of(2024, 1, 15);
    private static final Long TEAM_ID = 7L;
    private static final Long MEMBER_ID = 2L;

    private User currentUser;

    @BeforeEach
    void stubUser() {
        currentUser = new User();
        currentUser.setId(1L);
        when(checkHelper.currentUser()).thenReturn(currentUser);
    }

    private MetricSnapshot dailySnapshot(MetricType type, LocalDate date, double value) {
        MetricSnapshot s = new MetricSnapshot();
        s.setMetricType(type);
        s.setDate(date);
        s.setValue(value);
        return s;
    }

    private MetricSnapshot aggregateSnapshot(MetricType type, double value, LocalDate from, LocalDate to) {
        MetricSnapshot s = new MetricSnapshot();
        s.setMetricType(type);
        s.setDate(to);
        s.setValue(value);
        s.setPeriodFrom(from);
        s.setPeriodTo(to);
        return s;
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
    @WithMockUser
    void calculate_validRange_invokesMetricsService() throws Exception {
        mvc.perform(post("/api/metrics/calculate")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk());

        verify(metricsService).calculateDailyMetrics(currentUser.getId(), FROM, TO);
    }

    // =========================================================================
    // Personal daily-series endpoints (delegate to getPersonalDailySeries)
    // =========================================================================

    @Test
    @WithMockUser
    void getDailyPrCreated_returnsSeries() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(
                any(), eq(MetricType.DAILY_PR_CREATED), eq(FROM), eq(TO)))
                .thenReturn(List.of(dailySnapshot(MetricType.DAILY_PR_CREATED, DAY, 2)));

        mvc.perform(get("/api/metrics/daily-pr-created")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value(2.0))
                .andExpect(jsonPath("$[0].metricType").value("DAILY_PR_CREATED"));
    }

    @Test
    @WithMockUser
    void getDailyPrMerged_returnsSeries() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(
                any(), eq(MetricType.DAILY_PR_MERGED), eq(FROM), eq(TO)))
                .thenReturn(List.of(dailySnapshot(MetricType.DAILY_PR_MERGED, DAY, 1)));

        mvc.perform(get("/api/metrics/daily-pr-merged")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value(1.0))
                .andExpect(jsonPath("$[0].metricType").value("DAILY_PR_MERGED"));
    }

    @Test
    @WithMockUser
    void getDailyIssuesCreated_returnsSeries() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(
                any(), eq(MetricType.DAILY_ISSUES_CREATED), eq(FROM), eq(TO)))
                .thenReturn(List.of(dailySnapshot(MetricType.DAILY_ISSUES_CREATED, DAY, 4)));

        mvc.perform(get("/api/metrics/daily-issues-created")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value(4.0))
                .andExpect(jsonPath("$[0].metricType").value("DAILY_ISSUES_CREATED"));
    }

    @Test
    @WithMockUser
    void getDailyIssuesClosed_returnsSeries() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(
                any(), eq(MetricType.DAILY_ISSUES_CLOSED), eq(FROM), eq(TO)))
                .thenReturn(List.of(dailySnapshot(MetricType.DAILY_ISSUES_CLOSED, DAY, 3)));

        mvc.perform(get("/api/metrics/daily-issues-closed")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value(3.0))
                .andExpect(jsonPath("$[0].metricType").value("DAILY_ISSUES_CLOSED"));
    }

    // =========================================================================
    // Personal aggregate endpoints (delegate to getPersonalLeadTimeAggregate)
    // =========================================================================

    @Test
    @WithMockUser
    void getPrFirstCommitLeadTimeMedian_returnsAggregate() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(
                any(), eq(MetricType.PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN), eq(FROM), eq(TO)))
                .thenReturn(List.of(aggregateSnapshot(MetricType.PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN, 12.0, FROM, TO)));

        mvc.perform(get("/api/metrics/pr-first-commit-lead-time")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(12.0))
                .andExpect(jsonPath("$.metricType").value("PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN"));
    }

    @Test
    @WithMockUser
    void getReviewResponseTimeMedian_returnsAggregate() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(
                any(), eq(MetricType.REVIEW_RESPONSE_TIME_HOURS_MEDIAN), eq(FROM), eq(TO)))
                .thenReturn(List.of(aggregateSnapshot(MetricType.REVIEW_RESPONSE_TIME_HOURS_MEDIAN, 4.5, FROM, TO)));

        mvc.perform(get("/api/metrics/review-response-time")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(4.5))
                .andExpect(jsonPath("$.metricType").value("REVIEW_RESPONSE_TIME_HOURS_MEDIAN"));
    }

    @Test
    @WithMockUser
    void getIssueLeadTimeMedian_returnsAggregate() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(
                any(), eq(MetricType.ISSUE_LEAD_TIME_HOURS_MEDIAN), eq(FROM), eq(TO)))
                .thenReturn(List.of(aggregateSnapshot(MetricType.ISSUE_LEAD_TIME_HOURS_MEDIAN, 36.0, FROM, TO)));

        mvc.perform(get("/api/metrics/issue-lead-time")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(36.0))
                .andExpect(jsonPath("$.metricType").value("ISSUE_LEAD_TIME_HOURS_MEDIAN"));
    }

    @Test
    @WithMockUser
    void getAfterHoursRatio_returnsAggregate() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(
                any(), eq(MetricType.AFTER_HOURS_COMMIT_RATIO), eq(FROM), eq(TO)))
                .thenReturn(List.of(aggregateSnapshot(MetricType.AFTER_HOURS_COMMIT_RATIO, 0.2, FROM, TO)));

        mvc.perform(get("/api/metrics/after-hours-commit-ratio")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(0.2));
    }

    @Test
    @WithMockUser
    void getRefactorRatio_returnsAggregate() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(
                any(), eq(MetricType.REFACTOR_RATIO), eq(FROM), eq(TO)))
                .thenReturn(List.of(aggregateSnapshot(MetricType.REFACTOR_RATIO, 0.3, FROM, TO)));

        mvc.perform(get("/api/metrics/refactor-ratio")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(0.3));
    }

    @Test
    @WithMockUser
    void getDeepWorkStreak_returnsAggregate() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(
                any(), eq(MetricType.DEEP_WORK_STREAK_DAYS), eq(FROM), eq(TO)))
                .thenReturn(List.of(aggregateSnapshot(MetricType.DEEP_WORK_STREAK_DAYS, 5.0, FROM, TO)));

        mvc.perform(get("/api/metrics/deep-work-streak")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(5.0));
    }

    @Test
    @WithMockUser
    void getMergeFrequency_returnsAggregate() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(
                any(), eq(MetricType.MERGE_TO_MAIN_FREQUENCY_PER_WEEK), eq(FROM), eq(TO)))
                .thenReturn(List.of(aggregateSnapshot(MetricType.MERGE_TO_MAIN_FREQUENCY_PER_WEEK, 3.0, FROM, TO)));

        mvc.perform(get("/api/metrics/merge-to-main-frequency-per-week")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(3.0));
    }

    @Test
    @WithMockUser
    void getKnowledgeSilo_returnsAggregate() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(
                any(), eq(MetricType.KNOWLEDGE_SILO_SCORE), eq(FROM), eq(TO)))
                .thenReturn(List.of(aggregateSnapshot(MetricType.KNOWLEDGE_SILO_SCORE, 0.6, FROM, TO)));

        mvc.perform(get("/api/metrics/knowledge-silo-score")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(0.6));
    }

    @Test
    @WithMockUser
    void getPrSizeComplexity_returnsAggregate() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(
                any(), eq(MetricType.PR_SIZE_COMPLEXITY_SCORE), eq(FROM), eq(TO)))
                .thenReturn(List.of(aggregateSnapshot(MetricType.PR_SIZE_COMPLEXITY_SCORE, 120.0, FROM, TO)));

        mvc.perform(get("/api/metrics/pr-size-complexity")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(120.0));
    }

    @Test
    @WithMockUser
    void getMergeWithoutReview_returnsAggregate() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(
                any(), eq(MetricType.MERGE_WITHOUT_REVIEW_RATIO), eq(FROM), eq(TO)))
                .thenReturn(List.of(aggregateSnapshot(MetricType.MERGE_WITHOUT_REVIEW_RATIO, 0.1, FROM, TO)));

        mvc.perform(get("/api/metrics/merge-without-review")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(0.1));
    }

    // =========================================================================
    // Focus ratio endpoints
    // =========================================================================

    @Test
    @WithMockUser
    void getFocusRatioSeries_returnsSeries() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(
                any(), eq(MetricType.FOCUS_RATIO_DAYS_TASKS), eq(FROM), eq(TO)))
                .thenReturn(List.of(dailySnapshot(MetricType.FOCUS_RATIO_DAYS_TASKS, DAY, 0.8)));

        mvc.perform(get("/api/metrics/focus-ratio/series")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value(0.8))
                .andExpect(jsonPath("$[0].metricType").value("FOCUS_RATIO_DAYS_TASKS"));
    }

    @Test
    @WithMockUser
    void getFocusRatioAggregate_computesWeekdayRatio() throws Exception {
        // 2024-01-01 (Mon) .. 2024-01-07 (Sun) -> 5 weekdays; 3 active days -> ratio = 0.6
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 7);
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(
                any(), eq(MetricType.FOCUS_RATIO_DAYS_TASKS), eq(from), eq(to)))
                .thenReturn(List.of(
                        dailySnapshot(MetricType.FOCUS_RATIO_DAYS_TASKS, from, 1),
                        dailySnapshot(MetricType.FOCUS_RATIO_DAYS_TASKS, from.plusDays(1), 1),
                        dailySnapshot(MetricType.FOCUS_RATIO_DAYS_TASKS, from.plusDays(2), 1)
                ));

        mvc.perform(get("/api/metrics/focus-ratio")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(0.6))
                .andExpect(jsonPath("$.metricType").value("FOCUS_RATIO_DAYS_TASKS"));
    }

    // =========================================================================
    // Team endpoints
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

    // =========================================================================
    // Backfill
    // =========================================================================

    @Test
    @WithMockUser
    void backfill_validRange_returns202() throws Exception {
        LocalDate to = LocalDate.now().minusDays(2);
        LocalDate from = to.minusDays(5);

        mvc.perform(post("/api/metrics/backfill")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isAccepted());

        verify(metricsService).calculateDailyMetrics(currentUser.getId(), from, to);
    }

    @Test
    @WithMockUser
    void backfill_fromAfterTo_returns400() throws Exception {
        LocalDate to = LocalDate.now().minusDays(5);
        LocalDate from = to.minusDays(1).plusDays(2); // from is after to

        mvc.perform(post("/api/metrics/backfill")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void backfill_toIsToday_returns400() throws Exception {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(3);

        mvc.perform(post("/api/metrics/backfill")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Anomalies + freshness
    // =========================================================================

    @Test
    @WithMockUser
    void getAnomalies_returnsFlags() throws Exception {
        when(anomalyService.computeAnomalies(eq(currentUser), eq(FROM), eq(TO)))
                .thenReturn(Map.of(MetricType.DAILY_COMMITS_COUNT, true, MetricType.DAILY_CHURN_RATIO, false));

        mvc.perform(get("/api/metrics/anomalies")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.DAILY_COMMITS_COUNT").value(true))
                .andExpect(jsonPath("$.DAILY_CHURN_RATIO").value(false));
    }

    @Test
    @WithMockUser
    void getFreshness_whenExists_returnsDate() throws Exception {
        when(snapshotService.findMaxPersonalDate(currentUser.getId()))
                .thenReturn(Optional.of(LocalDate.of(2024, 1, 31)));

        mvc.perform(get("/api/metrics/freshness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricsComputedThrough").value("2024-01-31"));
    }

    @Test
    @WithMockUser
    void getFreshness_whenNone_returnsEmptyMap() throws Exception {
        when(snapshotService.findMaxPersonalDate(currentUser.getId()))
                .thenReturn(Optional.empty());

        mvc.perform(get("/api/metrics/freshness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
