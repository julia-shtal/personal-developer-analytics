package com.juliashtal.devanalytics.git.model.dto;

import com.juliashtal.devanalytics.git.model.GitCommitEntity;

import java.time.Instant;

/**
 * Git commit as returned to the client.
 */
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
        String parentHash,
        String authorGithubLogin
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
                e.getParentHash(),
                e.getAuthorGithubLogin()
        );
    }
}

