package com.juliashtal.devanalytics.ai.model;

import lombok.Builder;

import java.time.Instant;

@Builder
public record ConversationDto(Long id, Instant createdAt) {
}
