package com.juliashtal.devanalytics.ai.service;

import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.ai.model.ValidationReport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Drops insights that violate groundedness rules before a summary is stored: an insight must name
 * a metric present in the supplied context, and an insight on a metric flagged anomalous in that
 * context must carry an explanation. A blank metric names nothing and is exempt from the first
 * rule — it is the flat-string fallback for an unstructured model response, not a claim about data.
 *
 * <p>Separately, every number in a kept insight's text is checked against the numbers actually
 * supplied in the context; a number with no source is flagged in the report but the insight is
 * never dropped or rewritten for it — this is a measurement, not a gate.</p>
 */
@Component
public class SummaryValidator {

    /** Mirrors the "insights: 5-8 items" rule stated in {@link SystemPrompts}. */
    static final int MIN_INSIGHTS = 5;
    static final int MAX_INSIGHTS = 8;

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    /**
     * @param metricAliases every string the model may legitimately use to name a context metric
     *                      (its human label and its enum key), mapped to the canonical human label
     */
    public Result validate(List<MetricsSummaryDto.InsightDto> insights, Map<String, String> metricAliases,
                            Set<String> anomalousMetricNames, Set<String> groundedNumbers) {
        List<MetricsSummaryDto.InsightDto> kept = new ArrayList<>();
        int droppedUnknownMetric = 0;
        int droppedMissingExplanation = 0;
        int ungroundedNumberCount = 0;

        for (MetricsSummaryDto.InsightDto insight : insights) {
            String metric = insight.getMetric();
            boolean namesAMetric = metric != null && !metric.isBlank();
            String canonicalMetric = namesAMetric ? metricAliases.get(metric) : null;

            if (namesAMetric && canonicalMetric == null) {
                droppedUnknownMetric++;
                continue;
            }
            if (namesAMetric && anomalousMetricNames.contains(canonicalMetric) && isBlank(insight.getExplanation())) {
                droppedMissingExplanation++;
                continue;
            }
            if (namesAMetric && !canonicalMetric.equals(metric)) {
                insight.setMetric(canonicalMetric);
            }
            ungroundedNumberCount += ungroundedNumbersIn(insight.getText(), groundedNumbers);
            kept.add(insight);
        }

        boolean inRange = kept.size() >= MIN_INSIGHTS && kept.size() <= MAX_INSIGHTS;
        return new Result(kept, new ValidationReport(droppedUnknownMetric, droppedMissingExplanation,
                kept.size(), inRange, ungroundedNumberCount));
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
