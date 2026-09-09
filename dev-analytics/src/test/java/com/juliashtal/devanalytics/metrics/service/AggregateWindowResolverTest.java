package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.service.AggregateWindowResolver.Reduction;
import com.juliashtal.devanalytics.metrics.service.AggregateWindowResolver.ResolvedAggregate;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for the per-metric reduction rules. The reduction is the part of window
 * resolution that cannot be generic: combining three weeks of PR lead times is a different
 * operation from combining three weeks of review counts, and for three metrics it is not a
 * valid operation at all.
 */
class AggregateWindowResolverTest {

    private final AggregateWindowResolver resolver = new AggregateWindowResolver();

    private static final LocalDate W1_FROM = LocalDate.of(2024, 1, 1);
    private static final LocalDate W1_TO   = LocalDate.of(2024, 1, 7);
    private static final LocalDate W2_FROM = LocalDate.of(2024, 1, 8);
    private static final LocalDate W2_TO   = LocalDate.of(2024, 1, 14);
    private static final LocalDate W3_FROM = LocalDate.of(2024, 1, 15);
    private static final LocalDate W3_TO   = LocalDate.of(2024, 1, 21);

    @Test
    void resolve_noRows_returnsEmptyRatherThanZero() {
        assertThat(resolver.resolve(List.of(), MetricType.PR_LEAD_TIME_HOURS_MEDIAN)).isEmpty();
    }

    @Test
    void resolve_medianMetricOverThreeWeeks_takesTheMedianOfThePerWeekMedians() {
        Optional<ResolvedAggregate> resolved = resolver.resolve(List.of(
                window(W1_FROM, W1_TO, 10.0),
                window(W2_FROM, W2_TO, 50.0),
                window(W3_FROM, W3_TO, 30.0)), MetricType.PR_LEAD_TIME_HOURS_MEDIAN);

        assertThat(resolved).hasValueSatisfying(r -> {
            assertThat(r.value()).isEqualTo(30.0);
            assertThat(r.periodFrom()).isEqualTo(W1_FROM);
            assertThat(r.periodTo()).isEqualTo(W3_TO);
        });
    }

    @Test
    void resolve_countMetricOverThreeWeeks_sumsTheWindows() {
        // Weekly review counts are disjoint, so they add up.
        Optional<ResolvedAggregate> resolved = resolver.resolve(List.of(
                window(W1_FROM, W1_TO, 4.0),
                window(W2_FROM, W2_TO, 6.0),
                window(W3_FROM, W3_TO, 1.0)), MetricType.REVIEW_PARTICIPATION_COUNT);

        assertThat(resolved).hasValueSatisfying(r -> assertThat(r.value()).isEqualTo(11.0));
    }

    @Test
    void resolve_ratioMetricOverThreeWeeks_averagesTheWindows() {
        // Biased — the underlying counts are not stored, so weeks are weighted equally
        // rather than by volume — but bounded, and the reported window says what it covers.
        Optional<ResolvedAggregate> resolved = resolver.resolve(List.of(
                window(W1_FROM, W1_TO, 0.2),
                window(W2_FROM, W2_TO, 0.5),
                window(W3_FROM, W3_TO, 0.8)), MetricType.AFTER_HOURS_COMMIT_RATIO);

        assertThat(resolved).hasValueSatisfying(r -> assertThat(r.value()).isCloseTo(0.5, within(1e-9)));
    }

    @Test
    void resolve_nonDecomposableMetric_reportsOneStoredWindowInsteadOfCombiningThem() {
        // A deep-work streak may run across a window boundary, so summing or averaging
        // per-window streaks would state something that was never measured. Report the
        // widest stored window verbatim and label the figure with it.
        Optional<ResolvedAggregate> resolved = resolver.resolve(List.of(
                window(W1_FROM, W1_TO, 3.0),
                window(W2_FROM, LocalDate.of(2024, 1, 28), 5.0),   // a wider window
                window(W3_FROM, W3_TO, 2.0)), MetricType.DEEP_WORK_STREAK_DAYS);

        assertThat(resolved).hasValueSatisfying(r -> {
            assertThat(r.value()).isEqualTo(5.0);
            assertThat(r.periodFrom()).isEqualTo(W2_FROM);
            assertThat(r.periodTo()).isEqualTo(LocalDate.of(2024, 1, 28));
        });
    }

    @Test
    void resolve_nonDecomposableMetricWithEqualWindows_prefersTheMostRecent() {
        Optional<ResolvedAggregate> resolved = resolver.resolve(List.of(
                window(W1_FROM, W1_TO, 3.0),
                window(W3_FROM, W3_TO, 9.0)), MetricType.COMMITS_PER_WEEK_AVG);

        assertThat(resolved).hasValueSatisfying(r -> {
            assertThat(r.value()).isEqualTo(9.0);
            assertThat(r.periodFrom()).isEqualTo(W3_FROM);
        });
    }

    @Test
    void perWindow_crossRepositoryRowsInOneWindow_collapseToASingleObservation() {
        // Three repositories, one week: the week contributes one value, so a user with more
        // repositories does not get a longer series than one with fewer.
        List<AggregateWindowResolver.WindowValue> perWindow = resolver.perWindow(List.of(
                window(W1_FROM, W1_TO, 10.0),
                window(W1_FROM, W1_TO, 20.0),
                window(W1_FROM, W1_TO, 60.0)), MetricType.PR_LEAD_TIME_HOURS_MEDIAN);

        assertThat(perWindow).singleElement().satisfies(w -> {
            assertThat(w.value()).isEqualTo(20.0);
            assertThat(w.periodFrom()).isEqualTo(W1_FROM);
            assertThat(w.periodTo()).isEqualTo(W1_TO);
        });
    }

    @Test
    void perWindow_windowsOutOfOrder_areReturnedOldestFirst() {
        // The AI context computes trendPct from this order, so it has to be chronological.
        List<AggregateWindowResolver.WindowValue> perWindow = resolver.perWindow(List.of(
                window(W3_FROM, W3_TO, 3.0),
                window(W1_FROM, W1_TO, 1.0),
                window(W2_FROM, W2_TO, 2.0)), MetricType.PR_LEAD_TIME_HOURS_MEDIAN);

        assertThat(perWindow).extracting(AggregateWindowResolver.WindowValue::value)
                .containsExactly(1.0, 2.0, 3.0);
    }

    @Test
    void aggregateRowsAndDailyRows_partitionByStoredShapeNotByMetricType() {
        MetricSnapshot daily = new MetricSnapshot();
        daily.setDate(W1_FROM);
        daily.setValue(1.0);
        MetricSnapshot period = window(W1_FROM, W1_TO, 2.0);

        assertThat(AggregateWindowResolver.aggregateRows(List.of(daily, period))).containsExactly(period);
        assertThat(AggregateWindowResolver.dailyRows(List.of(daily, period))).containsExactly(daily);
    }

    @Test
    void reductionOf_dailyStoredType_isEmpty() {
        assertThat(resolver.reductionOf(MetricType.DAILY_COMMITS_COUNT)).isEmpty();
        assertThat(resolver.reductionOf(MetricType.PR_LEAD_TIME_HOURS_MEDIAN)).contains(Reduction.MEDIAN);
    }

    @Test
    void isCount_onlyTrueForTheCountMetric() {
        assertThat(resolver.isCount(MetricType.REVIEW_PARTICIPATION_COUNT)).isTrue();
        assertThat(resolver.isCount(MetricType.PR_LEAD_TIME_HOURS_MEDIAN)).isFalse();
        assertThat(resolver.isCount(MetricType.DAILY_COMMITS_COUNT)).isFalse();
    }

    /**
     * Every type the ISO-week grain applies to must be period-stored, or the weekly write
     * pass would produce rows no read path resolves.
     */
    @Test
    void everyAggregatePeriodType_isDeclaredPeriodStored() {
        assertThat(resolver.periodStoredTypes())
                .containsAll(java.util.Arrays.stream(MetricType.values())
                        .filter(t -> t.aggregatePeriod).toList());
    }

    private static MetricSnapshot window(LocalDate from, LocalDate to, double value) {
        MetricSnapshot s = new MetricSnapshot();
        s.setDate(from);
        s.setPeriodFrom(from);
        s.setPeriodTo(to);
        s.setValue(value);
        return s;
    }
}
