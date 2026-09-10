package com.juliashtal.devanalytics.user.model;

/**
 * Published when a user's attribution identity effectively changed — an address added or
 * removed, a GitHub account linked or re-linked, a Jira accountId set.
 *
 * <p>Every stored metric for that user was computed against the previous identity, and
 * calculators only ever upsert days that have data: narrowing an identity leaves the rows it
 * used to produce in place, and the coverage ledger marks those days done so the backfill
 * never revisits them. The listener therefore drops the user's snapshots and coverage before
 * recomputing, rather than recalculating over them.
 *
 * <p>Published only on an effective change; re-saving the same value publishes nothing, so a
 * profile save that touches only the username does not trigger a full recompute.
 */
public record AuthorIdentityChangedEvent(Long userId) {}
