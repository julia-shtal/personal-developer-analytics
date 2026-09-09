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
 * Turns the AGGREGATE-shape rows returned by the window-resolution queries into a
 * single value plus the window that value was actually computed over.
 *
 * <p>Reads never match a stored window against the requested one, so a request that
 * spans several stored windows has to combine them. How is a property of the metric,
 * not of the query: a count sums, a ratio averages, a median takes the median of the
 * per-window medians. Three metrics cannot be recovered from sub-windows at all —
 * {@code DEEP_WORK_STREAK_DAYS} (a run may cross a window boundary),
 * {@code COMMITS_PER_WEEK_AVG} (already a per-week rate) and {@code KNOWLEDGE_SILO_SCORE}
 * (a share whose denominator is not stored). Those use {@link Reduction#WIDEST_WINDOW}:
 * one stored window is reported verbatim rather than silently reduced, and the caller
 * labels the figure with that window.
 *
 * <p>Cross-repository rows sharing one window are combined first, with the same
 * operation, so a multi-repo user yields one value per window before windows combine.
 */
@Service
public class AggregateWindowResolver {

    /** How values from several stored windows (or several repositories) combine into one. */
    public enum Reduction {
        /** Medians of sub-windows; the median of them is the honest summary. */
        MEDIAN,
        /** Ratios; the mean is biased because numerator and denominator are not stored, but bounded. */
        MEAN,
        /** Counts; sub-window counts are disjoint and add up. */
        SUM,
        /** Not decomposable from sub-windows — report one stored window verbatim. */
        WIDEST_WINDOW
    }

    /**
     * Every metric type stored in AGGREGATE shape, and how it reduces.
     *
     * <p>Exposed through {@link #periodStoredTypes()} so {@code AggregateStorageShapeDriftTest}
     * can assert this key set equals the set of types the calculators actually write periods
     * for. That drift check is the regression guard for TASK 01: the {@code aggregatePeriod}
     * flag marked five types while thirteen were period-stored, and every read path that
     * trusted the flag silently dropped the other eight.
     */
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

    /**
     * Every metric type stored in AGGREGATE shape. The drift test asserts this equals the
     * set of types the calculators actually write a period for.
     */
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
     * One value per stored window, oldest first, with cross-repository rows already
     * combined. Used where the read side wants a series rather than a single figure —
     * the AI context computes min, max, median, trend and anomaly over exactly this.
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

        // WIDEST_WINDOW metrics are not decomposable across windows, but rows sharing one
        // window differ only by repository, so combining those is still sound: take the median.
        Reduction crossRepo = reduction == Reduction.WIDEST_WINDOW ? Reduction.MEDIAN : reduction;

        return byWindow.entrySet().stream()
                .map(e -> new WindowValue(e.getKey().get(0), e.getKey().get(1),
                        combine(e.getValue(), crossRepo)))
                .toList();
    }

    /**
     * The single figure for these rows and the window it covers. Empty when there is
     * nothing stored, which the caller reports as "no data" rather than as zero over
     * the requested window.
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
