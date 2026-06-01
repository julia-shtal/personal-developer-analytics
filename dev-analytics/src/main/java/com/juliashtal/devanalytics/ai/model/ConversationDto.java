package com.juliashtal.devanalytics.ai.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ConversationDto {
    private Long id;
    private Instant createdAt;
}
