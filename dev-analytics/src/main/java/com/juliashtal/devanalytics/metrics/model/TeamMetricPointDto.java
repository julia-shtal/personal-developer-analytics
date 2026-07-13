package com.juliashtal.devanalytics.metrics.model;

import java.time.LocalDate;

/**
 * A single dated metric value attributed to a team member.
 */
public record TeamMetricPointDto(
        LocalDate date,
        double value,
        String metricType,
        Long userId,
        String username
) { }
