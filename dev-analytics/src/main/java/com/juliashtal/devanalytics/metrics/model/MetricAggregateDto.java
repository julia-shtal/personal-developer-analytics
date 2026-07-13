package com.juliashtal.devanalytics.metrics.model;

import java.time.LocalDate;

/**
 * A single metric aggregate over a period.
 */
public record MetricAggregateDto(
        MetricType metricType,
        double value,
        LocalDate periodFrom,
        LocalDate periodTo
) { }