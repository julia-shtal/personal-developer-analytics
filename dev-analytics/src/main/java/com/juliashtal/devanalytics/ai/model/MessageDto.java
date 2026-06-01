package com.juliashtal.devanalytics.ai.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class MessageDto {
    private Long id;
    private String role;
    private String content;
    private Instant createdAt;
}
