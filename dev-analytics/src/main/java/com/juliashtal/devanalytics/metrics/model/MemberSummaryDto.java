package com.juliashtal.devanalytics.metrics.model;

import java.time.Instant;
import java.util.Map;

/**
 * Per-member metric summary for a team overview.
 */
public record MemberSummaryDto(
        Long userId,
        String username,
        Map<MetricType, Double> metrics,
        boolean hasCustomAvatar,
        String avatarPreset,
        Instant lastActiveAt,
        String email
) { }
