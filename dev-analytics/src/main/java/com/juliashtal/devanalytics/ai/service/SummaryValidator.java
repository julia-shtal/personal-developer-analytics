package com.juliashtal.devanalytics.ai.service;

import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.ai.model.ValidationReport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Drops insights that fail the groundedness rules before a summary is stored: each must name a
 * metric present in the supplied context, carry an explanation when that metric is flagged
 * anomalous, and state no direction its metric's trend contradicts.
 *
 * <p>A blank metric is resolved from the insight's own text when that names exactly one context
 * metric, and dropped otherwise. Numbers are checked against the context and flagged in the
 * report, never gated.</p>
 */
@Component
public class SummaryValidator {

    /** Mirrors the "insights: 5-8 items" rule stated in {@link SystemPrompts}. */
    static final int MIN_INSIGHTS = 5;
    static final int MAX_INSIGHTS = 8;

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    /** Trend verbs only; comparatives and value-laden words ("higher", "improved") do not name a direction of change. */
    private static final Pattern RISE = Pattern.compile(
            "\\b(?:rose|rises|rising|risen|increase[sd]?|increasing|grew|grow[sn]?|growing|climbed|climbing|doubled)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FALL = Pattern.compile(
            "\\b(?:fell|falling|fallen|decrease[sd]?|decreasing|drop(?:s|ped|ping)?|declined?|declining|reduced|reducing|halved)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Validates without trend data, so the direction rule is skipped (the team context carries none).
     */
    public Result validate(List<MetricsSummaryDto.InsightDto> insights, Map<String, String> metricAliases,
                            Set<String> anomalousMetricNames, Set<String> groundedNumbers) {
        return validate(insights, metricAliases, anomalousMetricNames, groundedNumbers, Map.of());
    }

    /**
     * @param metricAliases      every string the model may legitimately use to name a context metric
     *                           (its human label and its enum key), mapped to the canonical human label
     * @param trendPctByMetric   {@code trendPct} of each context metric, keyed by canonical human label
     */
    public Result validate(List<MetricsSummaryDto.InsightDto> insights, Map<String, String> metricAliases,
                            Set<String> anomalousMetricNames, Set<String> groundedNumbers,
                            Map<String, Double> trendPctByMetric) {
        List<MetricsSummaryDto.InsightDto> kept = new ArrayList<>();
        int droppedUnknownMetric = 0;
        int droppedMissingExplanation = 0;
        int droppedDirectionMismatch = 0;
        int ungroundedNumberCount = 0;

        for (MetricsSummaryDto.InsightDto insight : insights) {
            String metric = insight.getMetric();
            String canonicalMetric = isBlank(metric)
                    ? resolveFromText(insight, metricAliases)
                    : metricAliases.get(metric);

            if (canonicalMetric == null) {
                droppedUnknownMetric++;
                continue;
            }
            if (anomalousMetricNames.contains(canonicalMetric) && isBlank(insight.getExplanation())) {
                droppedMissingExplanation++;
                continue;
            }
            if (contradictsTrend(insight, trendPctByMetric.get(canonicalMetric))) {
                droppedDirectionMismatch++;
                continue;
            }
            if (!canonicalMetric.equals(metric)) {
                insight.setMetric(canonicalMetric);
            }
            ungroundedNumberCount += ungroundedNumbersIn(insight.getText(), groundedNumbers);
            kept.add(insight);
        }

        boolean inRange = kept.size() >= MIN_INSIGHTS && kept.size() <= MAX_INSIGHTS;
        return new Result(kept, new ValidationReport(droppedUnknownMetric, droppedMissingExplanation,
                kept.size(), inRange, ungroundedNumberCount, droppedDirectionMismatch));
    }

    /** The canonical metric the insight's text names, or null when it names none or more than one. */
    private String resolveFromText(MetricsSummaryDto.InsightDto insight, Map<String, String> metricAliases) {
        String text = insight.getText();
        if (isBlank(text)) return null;
        Set<String> named = new HashSet<>();
        for (Map.Entry<String, String> alias : metricAliases.entrySet()) {
            Pattern whole = Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(alias.getKey())
                    + "(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE);
            if (whole.matcher(text).find()) {
                named.add(alias.getValue());
            }
        }
        return named.size() == 1 ? named.iterator().next() : null;
    }

    /** A zero trend supports neither direction, so any direction word contradicts it. */
    private boolean contradictsTrend(MetricsSummaryDto.InsightDto insight, Double trendPct) {
        if (trendPct == null) return false;
        String words = (insight.getText() == null ? "" : insight.getText()) + " "
                + (insight.getExplanation() == null ? "" : insight.getExplanation());
        return (RISE.matcher(words).find() && trendPct <= 0)
                || (FALL.matcher(words).find() && trendPct >= 0);
    }

    private int ungroundedNumbersIn(String text, Set<String> groundedNumbers) {
        if (text == null) return 0;
        int count = 0;
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            if (!groundedNumbers.contains(matcher.group())) {
                count++;
            }
        }
        return count;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public record Result(List<MetricsSummaryDto.InsightDto> insights, ValidationReport report) {
    }
}
