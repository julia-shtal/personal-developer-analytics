package com.juliashtal.devanalytics.git.model.dto;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;

import java.time.Instant;

public record GitRepositoryDto(
        Long id,
        Long dataSourceId,
        String name,
        String localPath,
        String lastFetchedCommitHash,
        Instant lastScanAt
) {
    public static GitRepositoryDto fromEntity(GitRepositoryEntity e) {
        return new GitRepositoryDto(
                e.getId(),
                e.getDataSourceConfig() != null ? e.getDataSourceConfig().getId() : null,
                e.getName(),
                e.getLocalPath(),
                e.getLastFetchedCommitHash(),
                e.getLastScanAt()
        );
    }
}

