package com.juliashtal.devanalytics.user.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * JPA entity for user_commit_emails. One address a user declares as theirs for commit attribution.
 *
 * <p>Commits carry whatever address the committer's Git client was configured with, which is
 * frequently not the account email: a GitHub noreply alias, a work address, a second machine.
 * Matching on the account email alone silently dropped those commits from every commit metric.
 *
 * <p>The address is globally unique, not unique per user: one address identifies exactly one
 * person, so a second user claiming it is a conflict to reject rather than a row to insert.
 * Without that rule the same commit would be attributed twice and double-counted in team rollups.
 * Normalisation to {@code lower(btrim(...))} is enforced by a CHECK constraint, so the stored
 * value is always directly comparable against {@code lower(git_commits.author_email)}.
 */
@Data
@Entity
@Table(name = "user_commit_emails")
public class UserCommitEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
