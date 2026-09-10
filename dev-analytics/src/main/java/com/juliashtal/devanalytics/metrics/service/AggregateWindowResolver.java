package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.juliashtal.devanalytics.metrics.model.MetricType.*;

/**
 * Reduces AGGREGATE-shape rows to one value plus the window it was computed over.
 *
 * <p>How several stored windows combine is a property of the metric, not the query — see
 * {@link Reduction}. Cross-repository rows sharing a window are combined first, so one value
 * per window reaches the reduction.</p>
 */
@Service
public class AggregateWindowResolver {

    /** How values from several stored windows (or several repositories) combine into one. */
    public enum Reduction {
        /** Medians of sub-windows; the median of them is the honest summary. */
        MEDIAN,
        /** Ratios; the mean is biased because the denominator is not stored, but bounded. */
        MEAN,
        /** Counts; sub-window counts are disjoint and add up. */
        SUM,
        /** Not decomposable from sub-windows — report one stored window verbatim. */
        WIDEST_WINDOW
    }

    /** Every metric type stored in AGGREGATE shape, and how it reduces. */
    static final Map<MetricType, Reduction> REDUCTIONS;

    static {
        Map<MetricType, Reduction> m = new EnumMap<>(MetricType.class);
        m.put(PR_LEAD_TIME_HOURS_MEDIAN, Reduction.MEDIAN);
        m.put(PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN, Reduction.MEDIAN);
        m.put(ISSUE_LEAD_TIME_HOURS_MEDIAN, Reduction.MEDIAN);
        m.put(REVIEW_RESPONSE_TIME_HOURS_MEDIAN, Reduction.MEDIAN);
        m.put(PR_SIZE_COMPLEXITY_SCORE, Reduction.MEDIAN);
        m.put(WIP_OPEN_PR_AGE_HOURS_MEDIAN, Reduction.MEDIAN);

        m.put(AFTER_HOURS_COMMIT_RATIO, Reduction.MEAN);
        m.put(REFACTOR_RATIO, Reduction.MEAN);
        m.put(MERGE_WITHOUT_REVIEW_RATIO, Reduction.MEAN);

        m.put(REVIEW_PARTICIPATION_COUNT, Reduction.SUM);

        m.put(DEEP_WORK_STREAK_DAYS, Reduction.WIDEST_WINDOW);
        m.put(COMMITS_PER_WEEK_AVG, Reduction.WIDEST_WINDOW);
        m.put(KNOWLEDGE_SILO_SCORE, Reduction.WIDEST_WINDOW);

        REDUCTIONS = Map.copyOf(m);
    }

    /** One stored window and the value covering it, after cross-repository rows are combined. */
    public record WindowValue(LocalDate periodFrom, LocalDate periodTo, double value) {}

    /** A single figure and the window it was actually computed over — never the requested window. */
    public record ResolvedAggregate(double value, LocalDate periodFrom, LocalDate periodTo) {}

    /** AGGREGATE-shape rows: those carrying a period. */
    public static List<MetricSnapshot> aggregateRows(List<MetricSnapshot> rows) {
        return rows.stream().filter(s -> s.getPeriodFrom() != null).toList();
    }

    /** DAILY-shape rows: those carrying no period. */
    public static List<MetricSnapshot> dailyRows(List<MetricSnapshot> rows) {
        return rows.stream().filter(s -> s.getPeriodFrom() == null).toList();
    }

    /** Every metric type stored in AGGREGATE shape; the drift test asserts the calculators agree. */
    public Set<MetricType> periodStoredTypes() {
        return REDUCTIONS.keySet();
    }

    /** How this metric reduces, or empty when it is not stored in AGGREGATE shape. */
    public Optional<Reduction> reductionOf(MetricType type) {
        return Optional.ofNullable(REDUCTIONS.get(type));
    }

    /** True when this metric is a count, so a read spanning several windows sums them. */
    public boolean isCount(MetricType type) {
        return REDUCTIONS.get(type) == Reduction.SUM;
    }

    /**
     * One value per stored window, oldest first, with cross-repository rows already combined.
     * Used where the read side wants a series rather than a single figure.
     */
    public List<WindowValue> perWindow(List<MetricSnapshot> aggregateRows, MetricType type) {
        Reduction reduction = REDUCTIONS.getOrDefault(type, Reduction.MEDIAN);

        Map<List<LocalDate>, List<Double>> byWindow = new LinkedHashMap<>();
        aggregateRows.stream()
                .sorted(Comparator.comparing(MetricSnapshot::getPeriodFrom)
                        .thenComparing(MetricSnapshot::getPeriodTo))
                .forEach(s -> byWindow
                        .computeIfAbsent(List.of(s.getPeriodFrom(), s.getPeriodTo()), k -> new ArrayList<>())
                        .add(s.getValue()));

        // Rows sharing a window differ only by repository, so WIDEST_WINDOW can still median them.
        Reduction crossRepo = reduction == Reduction.WIDEST_WINDOW ? Reduction.MEDIAN : reduction;

        return byWindow.entrySet().stream()
                .map(e -> new WindowValue(e.getKey().get(0), e.getKey().get(1),
                        combine(e.getValue(), crossRepo)))
                .toList();
    }

    /**
     * The single figure for these rows and the window it covers. Empty when nothing is stored,
     * which the caller reports as "no data" rather than as zero.
     */
    public Optional<ResolvedAggregate> resolve(List<MetricSnapshot> aggregateRows, MetricType type) {
        List<WindowValue> windows = perWindow(aggregateRows, type);
        if (windows.isEmpty()) return Optional.empty();

        Reduction reduction = REDUCTIONS.getOrDefault(type, Reduction.MEDIAN);

        if (reduction == Reduction.WIDEST_WINDOW) {
            WindowValue widest = windows.stream()
                    .max(Comparator
                            .comparingLong((WindowValue w) -> ChronoUnit.DAYS.between(w.periodFrom(), w.periodTo()))
                            .thenComparing(WindowValue::periodTo))
                    .orElseThrow();
            return Optional.of(new ResolvedAggregate(widest.value(), widest.periodFrom(), widest.periodTo()));
        }

        double value = combine(windows.stream().map(WindowValue::value).toList(), reduction);
        LocalDate from = windows.stream().map(WindowValue::periodFrom).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to   = windows.stream().map(WindowValue::periodTo).max(Comparator.naturalOrder()).orElseThrow();
        return Optional.of(new ResolvedAggregate(value, from, to));
    }

    private double combine(List<Double> values, Reduction reduction) {
        return switch (reduction) {
            case SUM  -> values.stream().mapToDouble(Double::doubleValue).sum();
            case MEAN -> values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            case MEDIAN, WIDEST_WINDOW -> median(values);
        };
    }

    private double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        if (n == 0) return 0.0;
        return n % 2 == 1
                ? sorted.get(n / 2)
                : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }
}
