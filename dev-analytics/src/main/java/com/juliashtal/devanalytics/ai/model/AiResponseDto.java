package com.juliashtal.devanalytics.ai.model;

import java.util.List;

public record AiResponseDto(String overview, List<String> insights, List<String> recommendations) {}
