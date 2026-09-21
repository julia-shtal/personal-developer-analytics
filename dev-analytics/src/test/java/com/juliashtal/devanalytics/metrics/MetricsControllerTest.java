package com.juliashtal.devanalytics.metrics;

import com.jayway.jsonpath.JsonPath;
import com.juliashtal.devanalytics.config.SystemClock;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsSkipReason;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.metrics.controller.MetricsController;
import com.juliashtal.devanalytics.metrics.model.BackfillResult;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.model.StatsCoverageDto;
import com.juliashtal.devanalytics.metrics.model.StatsCoverageRecordType;
import com.juliashtal.devanalytics.metrics.repository.MetricSnapshotRepository;
import com.juliashtal.devanalytics.metrics.service.AggregateWindowResolver;
import com.juliashtal.devanalytics.metrics.service.MetricBackfillService;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.metrics.service.MetricsAnomalyService;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.metrics.service.StatsCoverageService;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.git.service.RepoService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
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
@Import(AggregateWindowResolver.class)   // pure computation — a mock would defeat the assertions
@AutoConfigureMockMvc(addFilters = false)
class MetricsControllerTest {

    @Autowired MockMvc mvc;

    @MockBean MetricSnapshotService snapshotService;
    @MockBean MetricsService metricsService;
    @MockBean MetricsAnomalyService anomalyService;
    @MockBean RepoService repoService;
    @MockBean UserService userService;
    @MockBean CheckHelper checkHelper;
    @MockBean
    MetricSnapshotRepository snapshotRepository;
    @MockBean MetricBackfillService backfillService;
    @MockBean StatsCoverageService statsCoverageService;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;
    @MockBean SystemClock systemClock;

    private static final LocalDate FROM = LocalDate.of(2024, 1, 1);
    private static final LocalDate TO = LocalDate.of(2024, 1, 31);
    private static final LocalDate DAY = LocalDate.of(2024, 1, 15);

    /** The backfill guard reads the current day in the requester's zone, so the slice fixes it. */
    private static final LocalDate TODAY = LocalDate.of(2024, 2, 1);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    private User currentUser;

    @BeforeEach
    void stubCurrentUserAndClock() {
        currentUser = new User();
        currentUser.setId(1L);
        // Set explicitly: User defaults to Europe/Berlin, and the backfill guard reads this zone.
        currentUser.setTimezone("UTC");
        when(checkHelper.currentUser()).thenReturn(currentUser);
        when(systemClock.today(ZoneId.of("UTC"))).thenReturn(TODAY);
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
        s.setCalculatedAt(COMPUTED_AT);
        return s;
    }

    /** Every snapshot a calculation writes carries the instant it was computed. */
    private static final Instant COMPUTED_AT = Instant.parse("2026-03-08T14:30:00Z");

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

    @Test
    @WithMockUser
    void getWipOpenPrAge_reportsTheInstantItWasComputed() throws Exception {
        // An age ending at the moment of calculation cannot be read without that moment.
        MetricSnapshot row = aggregateSnapshot(
                MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN, 48.0, FROM, TO);
        when(snapshotService.findDateOfLatestCalculation(
                any(), eq(MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN), isNull()))
                .thenReturn(Optional.of(TO));
        when(snapshotService.getPersonalSnapshotsByMetricTypeAndDate(
                any(), eq(MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN), eq(TO)))
                .thenReturn(List.of(row));

        mvc.perform(get("/api/metrics/wip-open-pr-age"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(48.0))
                .andExpect(jsonPath("$.calculatedAt").value("2026-03-08T14:30:00Z"));
    }

    // =========================================================================
    // Read surface: an owner can read every metric type, and a cross-repository aggregate
    // does not accept a filter it cannot honour.
    // =========================================================================

    @Test
    @WithMockUser
    void getDailyCommitsAvgSize_returnsTheDailySeries() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(
                any(), eq(MetricType.DAILY_COMMITS_AVG_SIZE), eq(FROM), eq(TO)))
                .thenReturn(List.of(dailySnapshot(MetricType.DAILY_COMMITS_AVG_SIZE, FROM, 42.0)));

        mvc.perform(get("/api/metrics/daily-commits-avg-size")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value(42.0))
                .andExpect(jsonPath("$[0].metricType").value("DAILY_COMMITS_AVG_SIZE"));
    }

    @Test
    @WithMockUser
    void getKnowledgeSilo_ignoresARepoIdRatherThanAnsweringZero() throws Exception {
        // Stored as one cross-repository row, so a filter can never match - and 0.0 is a
        // value this metric already uses to mean "owns none of it".
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
                any(), eq(MetricType.KNOWLEDGE_SILO_SCORE), eq(FROM), eq(TO)))
                .thenReturn(List.of(aggregateSnapshot(MetricType.KNOWLEDGE_SILO_SCORE, 0.66, FROM, TO)));

        mvc.perform(get("/api/metrics/knowledge-silo-score")
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .param("repoId", "34"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(0.66));

        // Never narrowed to a repository, whatever the caller sent.
        verify(snapshotService, never())
                .getMetricSnapshotsByUserAndMetricTypeAndRepositoryInWindow(
                        any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser
    void getDailyCommitsAvgSize_acrossRepositories_averagesRatherThanSums() throws Exception {
        // Summing two repositories would report 60 lines per commit, which no commit had.
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(
                any(), eq(MetricType.DAILY_COMMITS_AVG_SIZE), eq(FROM), eq(TO)))
                .thenReturn(List.of(dailySnapshot(MetricType.DAILY_COMMITS_AVG_SIZE, FROM, 40.0),
                                    dailySnapshot(MetricType.DAILY_COMMITS_AVG_SIZE, FROM, 20.0)));

        mvc.perform(get("/api/metrics/daily-commits-avg-size")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value(30.0));
    }

    // =========================================================================
    // Personal aggregate endpoints (delegate to getPersonalLeadTimeAggregate)
    // =========================================================================

    @Test
    @WithMockUser
    void getPrFirstCommitLeadTimeMedian_returnsAggregate() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
                any(), eq(MetricType.PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN), eq(FROM), eq(TO)))
                .thenReturn(List.of(aggregateSnapshot(MetricType.PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN, 12.0, FROM, TO)));

        mvc.perform(get("/api/metrics/pr-first-commit-to-merge-lead-time")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(12.0))
                .andExpect(jsonPath("$.metricType").value("PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN"));
    }

    @Test
    @WithMockUser
    void getReviewResponseTimeMedian_returnsAggregate() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
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
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
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
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
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
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
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
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
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
    void getCommitsPerWeekAvg_returnsAggregate() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
                any(), eq(MetricType.COMMITS_PER_WEEK_AVG), eq(FROM), eq(TO)))
                .thenReturn(List.of(aggregateSnapshot(MetricType.COMMITS_PER_WEEK_AVG, 3.0, FROM, TO)));

        mvc.perform(get("/api/metrics/commits-per-week-avg")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(3.0));
    }

    @Test
    @WithMockUser
    void getKnowledgeSilo_returnsAggregate() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
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
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
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
    void getWipOpenPrAge_noWindowParameters_returnsLatestValueWithItsCalculationDate() throws Exception {
        LocalDate calculatedAt = LocalDate.of(2026, 3, 8);
        when(snapshotService.findDateOfLatestCalculation(currentUser.getId(),
                MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN, null)).thenReturn(Optional.of(calculatedAt));
        when(snapshotService.getPersonalSnapshotsByMetricTypeAndDate(
                currentUser, MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN, calculatedAt))
                .thenReturn(List.of(
                        aggregateSnapshot(MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN, 24.0, calculatedAt, calculatedAt),
                        aggregateSnapshot(MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN, 72.0, calculatedAt, calculatedAt)));

        mvc.perform(get("/api/metrics/wip-open-pr-age"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricType").value("WIP_OPEN_PR_AGE_HOURS_MEDIAN"))
                .andExpect(jsonPath("$.value").value(48.0))
                .andExpect(jsonPath("$.calculatedAt").value("2026-03-08T14:30:00Z"))
                .andExpect(jsonPath("$.periodFrom").doesNotExist())
                .andExpect(jsonPath("$.periodTo").doesNotExist());
    }

    @Test
    @WithMockUser
    void getWipOpenPrAge_withFromAndTo_ignoresThemRatherThanClaimingAWindow() throws Exception {
        LocalDate calculatedAt = LocalDate.of(2026, 3, 8);
        when(snapshotService.findDateOfLatestCalculation(currentUser.getId(),
                MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN, null)).thenReturn(Optional.of(calculatedAt));
        when(snapshotService.getPersonalSnapshotsByMetricTypeAndDate(
                currentUser, MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN, calculatedAt))
                .thenReturn(List.of(
                        aggregateSnapshot(MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN, 24.0, calculatedAt, calculatedAt)));

        mvc.perform(get("/api/metrics/wip-open-pr-age")
                        .param("from", "2020-01-01").param("to", "2020-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculatedAt").value("2026-03-08T14:30:00Z"));
    }

    @Test
    @WithMockUser
    void getWipOpenPrAge_neverCalculated_returnsZeroWithNoCalculationDate() throws Exception {
        when(snapshotService.findDateOfLatestCalculation(currentUser.getId(),
                MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN, null)).thenReturn(Optional.empty());

        mvc.perform(get("/api/metrics/wip-open-pr-age"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(0.0))
                .andExpect(jsonPath("$.calculatedAt").doesNotExist());
    }

    @Test
    @WithMockUser
    void getMergeWithoutReview_returnsAggregate() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
                any(), eq(MetricType.MERGE_WITHOUT_REVIEW_RATIO), eq(FROM), eq(TO)))
                .thenReturn(List.of(aggregateSnapshot(MetricType.MERGE_WITHOUT_REVIEW_RATIO, 0.1, FROM, TO)));

        mvc.perform(get("/api/metrics/merge-without-review-ratio")
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
    // Backfill
    // =========================================================================

    @Test
    @WithMockUser
    void backfill_validRange_returns202() throws Exception {
        LocalDate to = YESTERDAY.minusDays(1);
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
        LocalDate to = YESTERDAY.minusDays(4);
        LocalDate from = to.minusDays(1).plusDays(2); // from is after to

        mvc.perform(post("/api/metrics/backfill")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void backfill_toIsToday_returns400() throws Exception {
        LocalDate to = TODAY;
        LocalDate from = to.minusDays(3);

        mvc.perform(post("/api/metrics/backfill")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isBadRequest());
    }

    /**
     * A requester west of UTC has not finished the UTC day yet, so the guard must reject a day a
     * UTC reading would have allowed.
     */
    @Test
    @WithMockUser
    void backfill_toIsTodayInTheRequestersZoneButYesterdayInUtc_returns400() throws Exception {
        currentUser.setTimezone("America/Los_Angeles");
        when(systemClock.today(ZoneId.of("America/Los_Angeles"))).thenReturn(YESTERDAY);

        mvc.perform(post("/api/metrics/backfill")
                        .param("from", YESTERDAY.minusDays(3).toString())
                        .param("to", YESTERDAY.toString()))
                .andExpect(status().isBadRequest());

        verify(metricsService, never()).calculateDailyMetrics(anyLong(), any(), any());
    }

    /**
     * A requester east of UTC has already completed a day UTC is still in, and must be allowed to
     * backfill it — the case the UTC-anchored guard rejected.
     */
    @Test
    @WithMockUser
    void backfill_toIsYesterdayInTheRequestersZoneButTodayInUtc_returns202() throws Exception {
        currentUser.setTimezone("Pacific/Auckland");
        when(systemClock.today(ZoneId.of("Pacific/Auckland"))).thenReturn(TODAY.plusDays(1));

        mvc.perform(post("/api/metrics/backfill")
                        .param("from", TODAY.minusDays(3).toString())
                        .param("to", TODAY.toString()))
                .andExpect(status().isAccepted());

        verify(metricsService).calculateDailyMetrics(currentUser.getId(), TODAY.minusDays(3), TODAY);
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
    void getFreshness_whenComputed_returnsDateAndCoverage() throws Exception {
        when(snapshotService.findMaxPersonalDate(currentUser.getId()))
                .thenReturn(Optional.of(LocalDate.of(2024, 1, 31)));
        when(backfillService.describeCoverage(currentUser.getId()))
                .thenReturn(new BackfillResult(0, 12,
                        LocalDate.of(2023, 6, 1), LocalDate.of(2024, 1, 31)));

        mvc.perform(get("/api/metrics/freshness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricsComputedThrough").value("2024-01-31"))
                .andExpect(jsonPath("$.coverageFrom").value("2023-06-01"))
                .andExpect(jsonPath("$.coverageTo").value("2024-01-31"))
                .andExpect(jsonPath("$.daysRemaining").value(12));
    }

    @Test
    @WithMockUser
    void getFreshness_whenNoneComputed_returnsNullsAndZeroRemaining() throws Exception {
        when(snapshotService.findMaxPersonalDate(currentUser.getId()))
                .thenReturn(Optional.empty());
        when(backfillService.describeCoverage(currentUser.getId()))
                .thenReturn(BackfillResult.empty());

        mvc.perform(get("/api/metrics/freshness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricsComputedThrough").doesNotExist())
                .andExpect(jsonPath("$.daysRemaining").value(0));
    }

    @Test
    @WithMockUser
    void getFreshness_neverComputesInTheRequestThread() throws Exception {
        when(snapshotService.findMaxPersonalDate(currentUser.getId()))
                .thenReturn(Optional.empty());
        when(backfillService.describeCoverage(currentUser.getId()))
                .thenReturn(BackfillResult.empty());

        mvc.perform(get("/api/metrics/freshness")).andExpect(status().isOk());

        verify(backfillService, never()).backfillUser(anyLong());
    }

    // =========================================================================
    // repoId entitlement — a client-supplied id must be checked, not just looked up
    // =========================================================================

    @Test
    @WithMockUser
    void getDailyCommits_repoIdNotAccessible_returnsNotFound() throws Exception {
        when(repoService.getAccessibleRepo(currentUser.getId(), 34L))
                .thenThrow(new NoSuchElementException("Git repo not found: 34"));

        mvc.perform(get("/api/metrics/daily-commits-count")
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .param("repoId", "34"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getPrLeadTime_repoIdNotAccessible_returnsNotFound() throws Exception {
        when(repoService.getAccessibleRepo(currentUser.getId(), 34L))
                .thenThrow(new NoSuchElementException("Git repo not found: 34"));

        mvc.perform(get("/api/metrics/pr-lead-time")
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .param("repoId", "34"))
                .andExpect(status().isNotFound());
    }

    /**
     * The response for a repo the caller may not reach must carry nothing that separates it
     * from one that does not exist — same status, same message.
     */
    @Test
    @WithMockUser
    void getDailyCommits_repoIdNotAccessible_bodyRevealsNothingAboutExistence() throws Exception {
        when(repoService.getAccessibleRepo(currentUser.getId(), 34L))
                .thenThrow(new NoSuchElementException("Git repo not found: 34"));

        mvc.perform(get("/api/metrics/daily-commits-count")
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .param("repoId", "34"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Git repo not found: 34"));
    }

    @Test
    @WithMockUser
    void getDailyCommits_repoIdGiven_neverResolvesRepoWithoutEntitlementCheck() throws Exception {
        when(repoService.getAccessibleRepo(currentUser.getId(), 34L))
                .thenThrow(new NoSuchElementException("Git repo not found: 34"));

        mvc.perform(get("/api/metrics/daily-commits-count")
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .param("repoId", "34"))
                .andExpect(status().isNotFound());

        verify(repoService, never()).getById(anyLong());
    }

    // ── stats coverage ─────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void getStatsCoverage_mixedStates_countsSumToRecordsInRange() throws Exception {
        when(statsCoverageService.describeCoverage(currentUser, FROM, TO, null)).thenReturn(List.of(
                new StatsCoverageDto(StatsCoverageRecordType.COMMIT,
                        StatsStatus.COMPLETE, null, 75L, 0.75),
                new StatsCoverageDto(StatsCoverageRecordType.COMMIT,
                        StatsStatus.SKIPPED, StatsSkipReason.DIFF_TOO_LARGE, 15L, 0.15),
                new StatsCoverageDto(StatsCoverageRecordType.COMMIT,
                        StatsStatus.SKIPPED, StatsSkipReason.RECORD_UNAVAILABLE, 10L, 0.10)));

        String json = mvc.perform(get("/api/metrics/stats-coverage")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].statsStatus").value("COMPLETE"))
                .andExpect(jsonPath("$[0].statsSkipReason").doesNotExist())
                .andExpect(jsonPath("$[1].statsSkipReason").value("DIFF_TOO_LARGE"))
                .andExpect(jsonPath("$[2].statsSkipReason").value("RECORD_UNAVAILABLE"))
                .andExpect(jsonPath("$[1].recordCount").value(15))
                .andExpect(jsonPath("$[1].share").value(0.15))
                .andReturn().getResponse().getContentAsString();

        // The spec's criterion: the reported states partition the range rather than overlap it.
        List<Integer> counts = JsonPath.read(json, "$[*].recordCount");
        assertThat(counts.stream().mapToLong(Integer::longValue).sum()).isEqualTo(100L);
    }

    @Test
    @WithMockUser
    void getStatsCoverage_repoIdGiven_checksEntitlementBeforeQuerying() throws Exception {
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(7L);
        when(repoService.getAccessibleRepo(1L, 7L)).thenReturn(repo);
        when(statsCoverageService.describeCoverage(currentUser, FROM, TO, 7L)).thenReturn(List.of());

        mvc.perform(get("/api/metrics/stats-coverage")
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .param("repoId", "7"))
                .andExpect(status().isOk());

        verify(repoService).getAccessibleRepo(1L, 7L);
    }

    @Test
    @WithMockUser
    void getStatsCoverage_inaccessibleRepo_doesNotReachTheService() throws Exception {
        when(repoService.getAccessibleRepo(1L, 99L))
                .thenThrow(new NoSuchElementException("Repository not accessible"));

        mvc.perform(get("/api/metrics/stats-coverage")
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .param("repoId", "99"))
                .andExpect(status().isNotFound());

        verify(statsCoverageService, never()).describeCoverage(any(), any(), any(), any());
    }

    @Test
    @WithMockUser
    void getStatsCoverage_emptyScope_returnsEmptyArray() throws Exception {
        when(statsCoverageService.describeCoverage(currentUser, FROM, TO, null)).thenReturn(List.of());

        mvc.perform(get("/api/metrics/stats-coverage")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
