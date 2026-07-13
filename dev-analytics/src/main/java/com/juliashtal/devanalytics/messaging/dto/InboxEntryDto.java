package com.juliashtal.devanalytics.messaging.dto;

import java.time.Instant;

/**
 * One conversation preview in a user's inbox.
 */
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