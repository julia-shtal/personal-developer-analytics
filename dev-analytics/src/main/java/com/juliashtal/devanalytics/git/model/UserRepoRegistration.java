package com.juliashtal.devanalytics.git.model;

import com.juliashtal.devanalytics.user.model.User;
import jakarta.persistence.*;
import lombok.Data;

/**
 * Grants a user read-access to a {@link GitRepositoryEntity} that may be canonically
 * owned by a different datasource. One canonical commit history is stored once; multiple
 * users subscribe via this join table rather than duplicating rows.
 *
 * <h3>Ownership semantics</h3>
 * <ul>
 *   <li>The datasource owner controls the sync schedule and API token.</li>
 *   <li>Subscribers inherit the canonical datasource's sync cadence.</li>
 *   <li>Deleting the canonical datasource cascades to all subscriptions.</li>
 * </ul>
 */
@Data
@Entity
@Table(
        name = "user_repo_registrations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "repository_id"})
)
/**
 * JPA entity for user_repo_registrations. Links a subscriber to a canonical repository.
 */
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
