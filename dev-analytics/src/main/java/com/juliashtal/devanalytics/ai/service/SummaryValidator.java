package com.juliashtal.devanalytics.ai.service;

import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.ai.model.ValidationReport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Drops insights that violate groundedness rules before a summary is stored: an insight must name
 * a metric present in the supplied context, and an insight on a metric flagged anomalous in that
 * context must carry an explanation. A blank metric names nothing and is exempt from the first
 * rule — it is the flat-string fallback for an unstructured model response, not a claim about data.
 */
@Component
public class SummaryValidator {

    /** Mirrors the "insights: 5-8 items" rule stated in {@link SystemPrompts}. */
    static final int MIN_INSIGHTS = 5;
    static final int MAX_INSIGHTS = 8;

    public Result validate(List<MetricsSummaryDto.InsightDto> insights, Set<String> validMetricNames,
                            Set<String> anomalousMetricNames) {
        List<MetricsSummaryDto.InsightDto> kept = new ArrayList<>();
        int droppedUnknownMetric = 0;
        int droppedMissingExplanation = 0;

        for (MetricsSummaryDto.InsightDto insight : insights) {
            String metric = insight.getMetric();
            boolean namesAMetric = metric != null && !metric.isBlank();

            if (namesAMetric && !validMetricNames.contains(metric)) {
                droppedUnknownMetric++;
                continue;
            }
            if (namesAMetric && anomalousMetricNames.contains(metric) && isBlank(insight.getExplanation())) {
                droppedMissingExplanation++;
                continue;
            }
            kept.add(insight);
        }

        boolean inRange = kept.size() >= MIN_INSIGHTS && kept.size() <= MAX_INSIGHTS;
        return new Result(kept, new ValidationReport(droppedUnknownMetric, droppedMissingExplanation,
                kept.size(), inRange));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public record Result(List<MetricsSummaryDto.InsightDto> insights, ValidationReport report) {
    }
}
