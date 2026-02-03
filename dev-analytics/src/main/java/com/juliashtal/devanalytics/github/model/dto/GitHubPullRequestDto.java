package com.juliashtal.devanalytics.github.model.dto;

import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;

import java.time.Instant;

public record GitHubPullRequestDto(
        Long id,
        int number,
        String title,
        String authorLogin,
        String state,
        boolean merged,
        Instant createdAt,
        Instant closedAt,
        Instant mergedAt,
        int additions,
        int deletions,
        int changedFiles,
        int commentsCount,
        int reviewCommentsCount,
        int commitsCount
) {
    public static GitHubPullRequestDto fromEntity(GitHubPullRequestEntity e) {
        return new GitHubPullRequestDto(
                e.getId(),
                e.getNumber(),
                e.getTitle(),
                e.getAuthorLogin(),
                e.getState(),
                e.isMerged(),
                e.getCreatedAt(),
                e.getClosedAt(),
                e.getMergedAt(),
                e.getAdditions(),
                e.getDeletions(),
                e.getChangedFiles(),
                e.getCommentsCount(),
                e.getReviewCommentsCount(),
                e.getCommitsCount()
        );
    }
}
