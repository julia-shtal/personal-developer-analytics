package com.juliashtal.devanalytics.messaging.dto;

import java.time.Instant;

public record DirectMessageDto(
        Long id,
        Long senderId,
        Long recipientId,
        String body,
        Instant createdAt,
        Instant readAt
) {}