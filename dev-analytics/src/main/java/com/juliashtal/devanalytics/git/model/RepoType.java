package com.juliashtal.devanalytics.git.model;

/** Discriminator for {@link GitRepositoryEntity} — see ADR-003. */
public enum RepoType {
    LOCAL,
    GITHUB
}
