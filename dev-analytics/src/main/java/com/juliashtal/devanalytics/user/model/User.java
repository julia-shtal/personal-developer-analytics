package com.juliashtal.devanalytics.user.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * JPA entity for users. Platform account with role, timezone, and GitHub login.
 */
@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String email;
    private String passwordHash;
    private String timezone = "Europe/Berlin";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.DEVELOPER;

    @Column(nullable = false)
    private int tokenVersion = 0;

    /**
     * GitHub login, kept for display and for addressing the account. Attribution never reads
     * it: it is free text, case-sensitive, and changes when the user renames themselves.
     */
    private String githubLogin;

    /**
     * Numeric GitHub account ID behind {@link #githubLogin}. Stable across renames and unique
     * per account, so this -- not the login -- is what PR, review and issue metrics match on.
     * Null until the login is resolved; a null identity attributes nothing rather than everything.
     */
    @Column(name = "github_user_id")
    private Long githubUserId;

    /** Jira accountId, the equivalent stable identifier for Jira issues. */
    @Column(name = "jira_account_id", length = 128)
    private String jiraAccountId;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    // Avatar — custom upload stored as BYTEA; preset stores a static SVG id like "preset-03"
    @Column(name = "avatar_data", columnDefinition = "BYTEA")
    private byte[] avatarData;

    @Column(name = "avatar_content_type", length = 32)
    private String avatarContentType;

    @Column(name = "avatar_preset", length = 64)
    private String avatarPreset;
}
