package com.juliashtal.devanalytics.metrics.model;

import java.time.LocalDate;

public record TeamMetricPointDto(
        LocalDate date,
        double value,
        String metricType,
        Long userId,
        String username
) { }
