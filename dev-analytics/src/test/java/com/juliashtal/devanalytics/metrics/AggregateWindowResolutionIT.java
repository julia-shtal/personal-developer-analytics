package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.ai.model.AggregatedMetricsContext;
import com.juliashtal.devanalytics.ai.service.AiContextBuilderService;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the window-resolution queries against a real database, since the predicate they turn
 * on is JPQL rather than Java and unit tests mock it away.
 *
 * <p>The scenario is a user whose metrics come from the nightly job plus a weekly pass, asking for
 * a seven-day AI context: it must carry all twelve metric types, period-stored ones included. The
 * LLM is not involved — this asserts the context, not the text. Needs the project Postgres
 * ({@code docker-compose up -d}).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class AggregateWindowResolutionIT {

    @Autowired UserRepository userRepository;
    @Autowired MetricSnapshotRepository snapshotRepository;
    @Autowired MetricSnapshotService metricSnapshotService;
    @Autowired AiContextBuilderService contextBuilder;

    /** Monday to Sunday — the grain the aggregate calculators now write. */
    private static final LocalDate WEEK_FROM = LocalDate.of(2024, 3, 4);
    private static final LocalDate WEEK_TO   = LocalDate.of(2024, 3, 10);

    private User user;

    @BeforeEach
    void seedOneWeekOfBothRowShapes() {
        user = new User();
        user.setUsername("window-resolution-it");
        user.setEmail("window-resolution-it@example.com");
        user.setPasswordHash("x");
        user.setRole(Role.DEVELOPER);
        user.setTimezone("UTC");
        user = userRepository.save(user);

        for (MetricType type : MetricType.values()) {
            if (type.aggregatePeriod) {
                // Written by the weekly aggregate pass: one row per ISO week.
                snapshotRepository.save(snapshot(type, WEEK_FROM, WEEK_FROM, WEEK_TO, 6.0));
            } else {
                // Written by the nightly series pass: one row per calendar day.
                WEEK_FROM.datesUntil(WEEK_TO.plusDays(1))
                        .forEach(day -> snapshotRepository.save(snapshot(type, day, null, null, 3.0)));
            }
        }
        snapshotRepository.flush();
    }

    @Test
    void buildPersonalContext_sevenDayWindowOverWeeklyRows_containsEveryContextMetricType() {
        AggregatedMetricsContext ctx = contextBuilder.buildPersonalContext(user, WEEK_FROM, WEEK_TO, null);

        assertThat(ctx.getMetrics()).hasSize(12);
        assertThat(ctx.getMetrics().keySet()).contains(
                MetricType.PR_LEAD_TIME_HOURS_MEDIAN.name(),
                MetricType.PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN.name(),
                MetricType.ISSUE_LEAD_TIME_HOURS_MEDIAN.name(),
                MetricType.REVIEW_RESPONSE_TIME_HOURS_MEDIAN.name(),
                MetricType.REVIEW_PARTICIPATION_COUNT.name());
    }

    @Test
    void findPersonalInWindow_windowStrictlyContained_returnsTheAggregateRow() {
        List<MetricSnapshot> rows = snapshotRepository.findPersonalInWindow(
                user, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, WEEK_FROM, WEEK_TO);

        assertThat(rows).singleElement().satisfies(s -> {
            assertThat(s.getPeriodFrom()).isEqualTo(WEEK_FROM);
            assertThat(s.getPeriodTo()).isEqualTo(WEEK_TO);
        });
    }

    @Test
    void findPersonalInWindow_windowOnlyPartiallyOverlapping_returnsNothing() {
        // A range ending mid-week does not contain the week, so containment must not match
        // it. Half a week's median is not the week's median.
        List<MetricSnapshot> rows = snapshotRepository.findPersonalInWindow(
                user, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, WEEK_FROM, WEEK_FROM.plusDays(3));

        assertThat(rows).isEmpty();
    }

    @Test
    void getMetricSnapshotsInWindow_requestNarrowerThanTheStoredWeek_fallsBackToTheCoveringWeek() {
        // Nothing is contained, so the read resolves to the week that covers the request and
        // reports that week's boundaries — never the requested three days.
        List<MetricSnapshot> rows = metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
                user, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, WEEK_FROM.plusDays(1), WEEK_FROM.plusDays(3));

        assertThat(rows).singleElement().satisfies(s -> {
            assertThat(s.getPeriodFrom()).isEqualTo(WEEK_FROM);
            assertThat(s.getPeriodTo()).isEqualTo(WEEK_TO);
        });
    }

    @Test
    void findPersonalInWindow_dailyShapeType_returnsOneRowPerDayAndNoPeriodRows() {
        List<MetricSnapshot> rows = snapshotRepository.findPersonalInWindow(
                user, MetricType.DAILY_COMMITS_COUNT, WEEK_FROM, WEEK_TO);

        assertThat(rows).hasSize(7);
        assertThat(rows).allSatisfy(s -> assertThat(s.getPeriodFrom()).isNull());
    }

    private MetricSnapshot snapshot(MetricType type, LocalDate date,
                                    LocalDate periodFrom, LocalDate periodTo, double value) {
        MetricSnapshot s = new MetricSnapshot();
        s.setUser(user);
        s.setMetricType(type);
        s.setDate(date);
        s.setPeriodFrom(periodFrom);
        s.setPeriodTo(periodTo);
        s.setValue(value);
        return s;
    }
}
