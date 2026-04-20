package com.juliashtal.devanalytics.metrics.model;

import java.util.Map;

public record MemberSummaryDto(
        Long userId,
        String username,
        Map<MetricType, Double> metrics
) { }