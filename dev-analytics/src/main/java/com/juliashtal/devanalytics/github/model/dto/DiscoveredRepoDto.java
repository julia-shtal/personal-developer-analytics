package com.juliashtal.devanalytics.github.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A GitHub repository discovered under a token, flagged if already attached.
 */
public record DiscoveredRepoDto(
        String fullName,
        @JsonProperty("private") boolean privateRepo,
        String defaultBranch,
        boolean alreadyAttached
) {}
