package com.juliashtal.devanalytics.ai.model;

import lombok.Builder;

import java.time.Instant;

@Builder
public record MessageDto(Long id, String role, String content, Instant createdAt) {
}
