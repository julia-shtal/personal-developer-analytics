package com.juliashtal.devanalytics.ai.model;

import java.time.LocalDate;

/**
 * Lightweight view of an active goal injected into {@link AggregatedMetricsContext}.
 * {@code currentValue} is the latest metric snapshot value for the period, or {@code null}
 * if no snapshot exists yet.
 */
public record GoalSummary(
        String metricType,
        double targetValue,
        LocalDate targetDate,
        Double currentValue
) {}
