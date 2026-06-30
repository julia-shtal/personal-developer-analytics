package com.juliashtal.devanalytics.ai.model;

import java.time.Instant;
import java.time.LocalDate;

/** Response representation of a developer goal. */
public record GoalDto(
        Long id,
        String metricType,
        double targetValue,
        LocalDate targetDate,
        Instant createdAt
) {}
