package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.metrics.controller.MetricsController;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.service.AggregateWindowResolver;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.metrics.service.MetricsAnomalyService;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.security.TeamAccessGuard;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.git.service.RepoService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.TeamService;
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

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Asserts that the cross-repo aggregation formulas are correct when repoId is not specified:
 *   - Count metrics: sum of per-repo daily values.
 *   - Ratio metrics (DAILY_CHURN_RATIO): average of per-repo daily values, not sum.
 *   - Median metrics (PR_LEAD_TIME_HOURS_MEDIAN): median of per-repo values, not arbitrary pick.
 */
@WebMvcTest(MetricsController.class)
@Import(AggregateWindowResolver.class)   // pure computation — a mock would defeat the assertions
@AutoConfigureMockMvc(addFilters = false)
class MetricsAggregationTest {

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
    private static final LocalDate TO   = LocalDate.of(2024, 1, 31);
    private static final LocalDate DAY  = LocalDate.of(2024, 1, 15);

    @BeforeEach
    void stubUser() {
        User user = new User();
        user.setId(1L);
        when(checkHelper.currentUser()).thenReturn(user);
    }

    private MetricSnapshot snapshot(MetricType type, LocalDate date, double value) {
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

    @Test
    @WithMockUser
    void dailyCommits_crossRepo_sumsValues() throws Exception {
        // Two repos each contribute 3 and 7 commits on the same day → total 10.
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(
                any(), eq(MetricType.DAILY_COMMITS_COUNT), eq(FROM), eq(TO)))
                .thenReturn(List.of(
                        snapshot(MetricType.DAILY_COMMITS_COUNT, DAY, 3),
                        snapshot(MetricType.DAILY_COMMITS_COUNT, DAY, 7)
                ));

        mvc.perform(get("/api/metrics/daily-commits-count")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value(10.0));
    }

    @Test
    @WithMockUser
    void dailyChurn_crossRepo_averagesValues() throws Exception {
        // Repo A: 0.3 churn, Repo B: 0.5 churn → cross-repo average = 0.4, NOT sum 0.8.
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(
                any(), eq(MetricType.DAILY_CHURN_RATIO), eq(FROM), eq(TO)))
                .thenReturn(List.of(
                        snapshot(MetricType.DAILY_CHURN_RATIO, DAY, 0.3),
                        snapshot(MetricType.DAILY_CHURN_RATIO, DAY, 0.5)
                ));

        mvc.perform(get("/api/metrics/daily-churn-ratio")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value(0.4));
    }

    @Test
    @WithMockUser
    void prLeadTime_crossRepo_computesMedian() throws Exception {
        // Repo A: 10h, Repo B: 20h → cross-repo median = 15h, NOT 20h (arbitrary last pick).
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
                any(), eq(MetricType.PR_LEAD_TIME_HOURS_MEDIAN), eq(FROM), eq(TO)))
                .thenReturn(List.of(
                        aggregateSnapshot(MetricType.PR_LEAD_TIME_HOURS_MEDIAN, 20.0, FROM, TO),
                        aggregateSnapshot(MetricType.PR_LEAD_TIME_HOURS_MEDIAN, 10.0, FROM, TO)
                ));

        mvc.perform(get("/api/metrics/pr-lead-time")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(15.0));
    }
}
