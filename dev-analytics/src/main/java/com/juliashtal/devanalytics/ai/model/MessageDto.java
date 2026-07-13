package com.juliashtal.devanalytics.ai.model;

import lombok.Builder;

import java.time.Instant;

/**
 * A single AI conversation message returned to the client.
 */
@Builder
public record MessageDto(Long id, String role, String content, Instant createdAt) {
}
