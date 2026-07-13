package com.juliashtal.devanalytics.ai.model;

import lombok.Builder;

import java.time.Instant;

/**
 * AI conversation summary returned to the client.
 */
@Builder
public record ConversationDto(Long id, Instant createdAt) {
}
