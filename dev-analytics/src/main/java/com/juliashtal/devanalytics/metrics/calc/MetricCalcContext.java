package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.user.model.AuthorIdentity;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable runtime context passed to every {@link MetricCalculator#calculate} call.
 * Calculators inject their own repositories via constructor injection; this record
 * carries only the per-invocation data.
 *
 * <p>{@code identity} carries the identifiers this user's records are matched by. Calculators
 * must filter on it, never on {@code user.getEmail()} or {@code user.getGithubLogin()}, which
 * are display values; one whose identifier is absent writes nothing at all.
 */
public record MetricCalcContext(
        User user,
        Team team,
        List<Long> repoIds,
        AuthorIdentity identity,
        Instant from,
        Instant to,
        LocalDate fromDate,
        LocalDate toDate
) {}
