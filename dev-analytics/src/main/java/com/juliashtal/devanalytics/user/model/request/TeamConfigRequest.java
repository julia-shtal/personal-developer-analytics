package com.juliashtal.devanalytics.user.model.request;

import jakarta.validation.constraints.Pattern;

/**
 * Request to update a team's visibility and AI-brief schedule.
 */
public record TeamConfigRequest(
        @Pattern(regexp = "PRIVATE|WORKSPACE|PUBLIC", message = "visibility must be PRIVATE, WORKSPACE, or PUBLIC")
        String visibility,
        String aiBriefSchedule
) {}
