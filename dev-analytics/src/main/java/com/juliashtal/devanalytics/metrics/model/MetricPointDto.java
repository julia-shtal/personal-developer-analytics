package com.juliashtal.devanalytics.metrics.model;

import java.time.LocalDate;

public record MetricPointDto(
        LocalDate date,
        double value,
        String metricType,
        LocalDate periodFrom,
        LocalDate periodTo
) {
    public static MetricPointDto fromEntity(MetricSnapshot s) {
        return new MetricPointDto(
                s.getDate(),
                s.getValue(),
                s.getMetricType().name(),
                s.getPeriodFrom(),
                s.getPeriodTo()
        );
    }
}