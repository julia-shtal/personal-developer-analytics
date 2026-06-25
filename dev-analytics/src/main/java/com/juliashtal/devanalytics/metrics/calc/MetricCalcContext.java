package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable runtime context passed to every {@link MetricCalculator#calculate} call.
 * Calculators inject their own repositories via constructor injection; this record
 * carries only the per-invocation data.
 */
public record MetricCalcContext(
        User user,
        Team team,
        List<Long> repoIds,
        Instant from,
        Instant to,
        LocalDate fromDate,
        LocalDate toDate
) {}
