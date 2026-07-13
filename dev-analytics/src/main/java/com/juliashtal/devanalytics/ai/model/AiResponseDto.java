package com.juliashtal.devanalytics.ai.model;

import java.util.List;

/**
 * Structured AI summary response: headline, overview, insights, and recommendations.
 */
public record AiResponseDto(
        String headline,
        String overview,
        List<InsightDto> insights,
        List<String> recommendations
) {
    /**
     * A single AI insight (kind, text, related metric).
     */
    public record InsightDto(String kind, String text, String metric) {}
}
