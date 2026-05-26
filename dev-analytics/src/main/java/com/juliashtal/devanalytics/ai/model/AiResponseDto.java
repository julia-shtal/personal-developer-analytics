package com.juliashtal.devanalytics.ai.model;

import java.util.List;

public record AiResponseDto(
        String headline,
        String overview,
        List<InsightDto> insights,
        List<String> recommendations
) {
    public record InsightDto(String kind, String text, String metric) {}
}
