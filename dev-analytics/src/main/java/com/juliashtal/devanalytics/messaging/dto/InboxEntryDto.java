package com.juliashtal.devanalytics.messaging.dto;

import java.time.Instant;

public record InboxEntryDto(
        Long partnerId,
        String partnerUsername,
        String partnerAvatarPreset,
        boolean partnerHasCustomAvatar,
        String lastBody,
        Instant lastMessageAt,
        Long lastSenderId,
        long unreadCount
) {}