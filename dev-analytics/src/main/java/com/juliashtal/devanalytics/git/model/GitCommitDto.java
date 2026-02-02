package com.juliashtal.devanalytics.git.model;

import java.time.Instant;

public record GitCommitDto(
        Long id,
        String hash,
        String authorName,
        String authorEmail,
        Instant authorDate,
        String message,
        int additions,
        int deletions,
        int filesChanged,
        String parentHash
) {
    public static GitCommitDto fromEntity(GitCommitEntity e) {
        return new GitCommitDto(
                e.getId(),
                e.getHash(),
                e.getAuthorName(),
                e.getAuthorEmail(),
                e.getAuthorDate(),
                e.getMessage(),
                e.getAdditions(),
                e.getDeletions(),
                e.getFilesChanged(),
                e.getParentHash()
        );
    }
}

