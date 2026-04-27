package com.juliashtal.devanalytics.git.model.dto;

public record RepoDto(
        Long id,
        String name,
        String repoFullName,
        String localPath,
        Long dataSourceId,
        boolean subscribed,
        String repoUrl
) {}
