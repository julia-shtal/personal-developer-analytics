package com.juliashtal.devanalytics.user.model;

public record AdminStatsDto(long activeUsers24h, long databaseSizeBytes, long aiCallsToday) {}
