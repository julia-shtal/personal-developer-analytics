package com.juliashtal.devanalytics.metrics.model;

import java.time.LocalDate;

public record MetricAggregateDto(
        MetricType metricType,
        double value,
        LocalDate periodFrom,
        LocalDate periodTo
) { }