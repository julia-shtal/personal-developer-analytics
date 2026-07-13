package com.juliashtal.devanalytics.messaging.dto;

import java.time.Instant;

/**
 * A direct message as returned to the client.
 */
public record DirectMessageDto(
        Long id,
        Long senderId,
        Long recipientId,
        String body,
        Instant createdAt,
        Instant readAt
) {}