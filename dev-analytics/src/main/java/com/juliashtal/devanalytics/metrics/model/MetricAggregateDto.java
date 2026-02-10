package com.juliashtal.devanalytics.metrics.model;

public record MetricAggregateDto(
        String metricType,
        double value,
        String dimensionsJson
) { }
