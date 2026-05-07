package com.juliashtal.devanalytics.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AggregatedMetricsContext {

    private LocalDate from;
    private LocalDate to;
    private String repoName;
    private Map<String, MetricAggregate> metrics = new LinkedHashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricAggregate {
        /** Minimum value, pre-formatted to 2 decimal places. */
        private String min;
        /** Maximum value, pre-formatted to 2 decimal places. */
        private String max;
        /** Median value, pre-formatted to 2 decimal places. */
        private String median;
        /** Sum over the period as a whole number (count metrics only; omitted for rate/ratio metrics). */
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private long total;
        /**
         * Trend: percentage change from the first-half average to the second-half average,
         * rounded to 1 decimal place. Positive = increasing; negative = declining.
         */
        private double trendPct;
        /** True if any single observation deviated more than 2σ from the period mean. */
        private boolean anomaly;
    }
}
