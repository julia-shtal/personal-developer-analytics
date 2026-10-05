package com.juliashtal.devanalytics.ai.model;

/**
 * Per-rule outcome of validating a parsed summary's insights before it is stored.
 */
public record ValidationReport(
        int droppedUnknownMetric,
        int droppedMissingExplanation,
        int finalInsightCount,
        boolean insightCountInRange,
        int ungroundedNumberCount,
        int droppedDirectionMismatch
) {
}
