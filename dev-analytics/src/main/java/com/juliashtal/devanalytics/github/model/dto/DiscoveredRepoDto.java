package com.juliashtal.devanalytics.github.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DiscoveredRepoDto(
        String fullName,
        @JsonProperty("private") boolean privateRepo,
        String defaultBranch,
        boolean alreadyAttached
) {}
