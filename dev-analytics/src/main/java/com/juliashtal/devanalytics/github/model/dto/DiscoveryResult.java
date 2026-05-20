package com.juliashtal.devanalytics.github.model.dto;

import java.util.List;

/** Internal result type for repo discovery — holds the discovered repos and a truncation flag. */
public record DiscoveryResult(List<DiscoveredRepoDto> repos, boolean truncated) {}
