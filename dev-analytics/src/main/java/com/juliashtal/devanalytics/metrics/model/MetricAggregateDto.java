package com.juliashtal.devanalytics.metrics.model;

public record MetricAggregateDto(
        MetricType metricType,
        double value,
        String dimensionsJson
) { }
