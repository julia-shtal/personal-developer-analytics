package com.juliashtal.devanalytics.metrics.model;

import java.time.LocalDate;

/**
 * A metric that describes a moment rather than a period, reported with the date it was computed.
 *
 * <p>Distinct from {@link MetricAggregateDto} because there is no window to report: a point-in-time
 * figure cannot be recomputed for a past range, so labelling it with {@code periodFrom}/{@code periodTo}
 * would claim a measurement window that does not exist.</p>
 */
public record MetricPointInTimeDto(
        MetricType metricType,
        double value,
        LocalDate calculatedAt
) { }
