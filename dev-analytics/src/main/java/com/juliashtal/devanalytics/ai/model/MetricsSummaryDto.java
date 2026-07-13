package com.juliashtal.devanalytics.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Parsed AI summary (headline, overview, insights) for a metric scope and period.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsSummaryDto {

    private LocalDate from;
    private LocalDate to;
    /** PERSONAL, REPOSITORY, or TEAM */
    private String scope;
    /** Snapshot-in-time scope label: repo full name, Jira project key, or team name. */
    private String contextRepoName;
    private String headline;
    private String overview;
    private List<InsightDto> insights;
    private List<String> recommendations;
    private String rawModelOutput;
    private String modelName;
    private Instant generatedAt;

    /**
     * A single AI insight within a summary.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InsightDto {
        /** "positive", "risk", or "note" */
        private String kind;
        private String text;
        /** Human-readable metric name from the mapping in the system prompt. */
        private String metric;
        /**
         * One-sentence AI hypothesis for the likely cause of this anomaly.
         * Null for non-anomalous insights (model omits or returns null for the field).
         */
        private String explanation;
    }
}
