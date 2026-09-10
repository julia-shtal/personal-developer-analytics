package com.juliashtal.devanalytics.metrics.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * How far the current user's personal metrics have actually been computed.
 *
 * <p>{@code daysRemaining} makes coverage inspectable rather than inferred: a dashboard that
 * looks sparse because history is still being backfilled is distinguishable from one that is
 * sparse because there was no activity. Chapter 8 cites this number to state the window the
 * case-study metrics were computed over.
 *
 * @param metricsComputedThrough latest day for which any personal metric snapshot exists, or
 *                               {@code null} when nothing has been computed yet
 * @param coverageFrom           first day of collected history, in the user's timezone, or
 *                               {@code null} when there is no target range
 * @param coverageTo             last day the backfill targets — yesterday in the user's
 *                               timezone — or {@code null} in the same case
 * @param daysRemaining          days inside the collected range still awaiting calculation;
 *                               a primitive, so it is always serialised and reads 0 rather
 *                               than absent when there is nothing outstanding
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
