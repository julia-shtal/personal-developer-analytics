package com.juliashtal.devanalytics.ai.model;

import lombok.Data;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact team context sent to the LLM.
 * Each member carries period-aggregated metric values (not raw daily time series)
 * to keep the prompt small and within practical inference time.
 *
 * Daily activity metrics (DAILY_*) are summed over the period.
 * Aggregate metrics (lead times, ratios) are represented as their single period value.
 */
@Data
public class TeamMetricsContext {

    private LocalDate from;
    private LocalDate to;
    private String teamName;
    private int memberCount;
    private List<MemberMetrics> members;

    /**
     * Per-member metric aggregates within a team AI context.
     */
    @Data
    public static class MemberMetrics {
        private String username;
        /** key = MetricType name, value = period aggregate (sum or median). */
        private Map<String, Double> metrics = new LinkedHashMap<>();
    }
}
