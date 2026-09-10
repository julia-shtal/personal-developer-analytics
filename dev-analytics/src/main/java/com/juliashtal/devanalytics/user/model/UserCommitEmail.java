package com.juliashtal.devanalytics.user.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * JPA entity for user_commit_emails. One address a user declares as theirs for commit attribution.
 *
 * <p>Globally unique, not unique per user: one address identifies one person, so a second user
 * claiming it is a conflict to reject rather than a row to insert. A CHECK constraint enforces
 * {@code lower(btrim(...))}, keeping it comparable against {@code lower(git_commits.author_email)}.</p>
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
