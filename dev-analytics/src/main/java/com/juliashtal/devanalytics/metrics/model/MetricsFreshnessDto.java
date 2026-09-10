package com.juliashtal.devanalytics.metrics.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * How far the current user's personal metrics have actually been computed.
 *
 * <p>{@code daysRemaining} makes coverage inspectable rather than inferred, so a dashboard sparse
 * from an unfinished backfill is distinguishable from one sparse from no activity.</p>
 *
 * @param metricsComputedThrough latest day any personal snapshot exists for, or {@code null}
 * @param coverageFrom           first day of collected history in the user's timezone, or {@code null}
 * @param coverageTo             last day the backfill targets (yesterday), or {@code null}
 * @param daysRemaining          days in range still awaiting calculation; primitive, so 0 rather
 *                               than absent when nothing is outstanding
 */
@Schema(description = "Coverage of the current user's computed personal metrics")
public record MetricsFreshnessDto(

        @Schema(description = "Latest day for which any personal metric snapshot exists", example = "2026-09-08")
        LocalDate metricsComputedThrough,

        @Schema(description = "First day of collected history, in the user's timezone", example = "2026-03-01")
        LocalDate coverageFrom,

        @Schema(description = "Last day the backfill targets — yesterday in the user's timezone", example = "2026-09-08")
        LocalDate coverageTo,

        @Schema(description = "Days inside the collected range still awaiting calculation", example = "12")
        long daysRemaining) {
}
