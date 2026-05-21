package com.juliashtal.devanalytics.git.model;

import com.juliashtal.devanalytics.user.model.User;
import jakarta.persistence.*;
import lombok.Data;

/**
 * Canonical access-sharing mechanism for the content-addressed repository model (ADR-004).
 *
 * <p>A {@code UserRepoRegistration} row grants a user read-access to a
 * {@link GitRepositoryEntity} that may be canonically owned by a different datasource.
 * It is <em>not</em> a workaround for missing per-datasource rows — it is the
 * intentional way the platform shares one commit history across multiple users without
 * duplicating storage.
 *
 * <h3>Ownership semantics</h3>
 * <ul>
 *   <li>The user who owns the canonical {@code data_source_configs} row controls the
 *       sync schedule and API token used for collection.</li>
 *   <li>Subscribers inherit the canonical datasource's sync cadence; they cannot trigger
 *       independent collection for the same repository.</li>
 *   <li>If the canonical datasource is deleted, all subscriptions are removed via
 *       the cascade on {@code git_repositories.data_source_id}.</li>
 * </ul>
 *
 * <p>See {@code docs/adr/ADR-004-cross-ds-repo-sharing.md} for the full decision record.
 */
@Data
@Entity
@Table(
        name = "user_repo_registrations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "repository_id"})
)
public class UserRepoRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private GitRepositoryEntity repository;
}
