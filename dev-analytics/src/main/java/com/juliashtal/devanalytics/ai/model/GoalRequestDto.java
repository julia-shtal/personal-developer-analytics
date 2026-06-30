package com.juliashtal.devanalytics.ai.model;

import java.time.LocalDate;

/**
 * Request body for creating a new developer goal.
 * {@code metricType} must be a valid {@link com.juliashtal.devanalytics.metrics.model.MetricType} name.
 */
public record GoalRequestDto(
        String metricType,
        double targetValue,
        LocalDate targetDate
) {}
