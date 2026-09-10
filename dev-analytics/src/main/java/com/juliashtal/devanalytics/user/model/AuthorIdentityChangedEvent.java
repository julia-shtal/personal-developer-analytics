package com.juliashtal.devanalytics.user.model;

/**
 * Published when a user's attribution identity effectively changed — an address added or
 * removed, a GitHub account linked or re-linked, a Jira accountId set.
 *
 * <p>The listener drops the user's snapshots and coverage before recomputing, because
 * calculators upsert and never delete. Published only on an effective change, so re-saving an
 * unchanged value does not trigger a full recompute.</p>
 */
public record AuthorIdentityChangedEvent(Long userId) {}
