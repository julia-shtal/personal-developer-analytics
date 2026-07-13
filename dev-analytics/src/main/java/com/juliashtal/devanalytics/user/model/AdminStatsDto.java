package com.juliashtal.devanalytics.user.model;

/**
 * Admin dashboard KPIs (active users, database size, AI calls today).
 */
public record AdminStatsDto(long activeUsers24h, long databaseSizeBytes, long aiCallsToday) {}
