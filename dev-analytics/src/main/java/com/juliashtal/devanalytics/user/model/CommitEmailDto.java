package com.juliashtal.devanalytics.user.model;

/**
 * One declared commit address, as returned to the client.
 */
public record CommitEmailDto(Long id, String email) {
    public static CommitEmailDto from(UserCommitEmail entity) {
        return new CommitEmailDto(entity.getId(), entity.getEmail());
    }
}
