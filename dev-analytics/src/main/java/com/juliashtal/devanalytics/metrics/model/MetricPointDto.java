package com.juliashtal.devanalytics.metrics.model;

import java.time.LocalDate;

public record MetricPointDto(
        LocalDate date,
        double value,
        String metricType,
        String dimensionsJson
) {
    public static MetricPointDto fromEntity(MetricSnapshot s) {
        return new MetricPointDto(s.getDate(), s.getValue(), s.getMetricType().name(), s.getDimensionsJson());
    }
}

