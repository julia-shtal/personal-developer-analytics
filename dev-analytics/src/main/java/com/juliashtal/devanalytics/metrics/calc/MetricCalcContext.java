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
 * <p>{@code identity} carries the identifiers this user's records are matched by, resolved
 * once per run. Calculators must filter on it and never on {@code user.getEmail()} or
 * {@code user.getGithubLogin()}: those are display values, and matching on them is what let
 * one person's records split across two identities or leak into another person's metrics.
 * A calculator whose required identifier is absent writes nothing at all — see the
 * {@code has*} predicates on {@link AuthorIdentity}.
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
