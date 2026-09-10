package com.juliashtal.devanalytics.user.repository;

import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for User (users). Lookups by username/email and search.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    /** Ownership check for identity claims: at most one user may hold a GitHub account. */
    Optional<User> findByGithubUserId(Long githubUserId);

    /** Ownership check for identity claims: at most one user may hold a Jira account. */
    Optional<User> findByJiraAccountId(String jiraAccountId);

    /** Users who named a GitHub login that was never resolved to a numeric account ID. */
    List<User> findByGithubLoginIsNotNullAndGithubUserIdIsNull();

    long countByGithubLoginIsNotNullAndGithubUserIdIsNull();

    @Query("SELECT u FROM User u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<User> searchByEmailOrUsername(String q);

    @Modifying
    @Query("UPDATE User u SET u.tokenVersion = u.tokenVersion + 1 WHERE u.id = :id")
    void incrementTokenVersion(Long id);

    @Modifying
    @Query(value = "UPDATE users SET last_active_at = NOW() WHERE id = :userId", nativeQuery = true)
    void touchLastActive(@Param("userId") Long userId);

    @Query(value = "SELECT COUNT(*) FROM users WHERE last_active_at > NOW() - INTERVAL '24 hours'", nativeQuery = true)
    long countActiveUsersLast24h();

    long countByRole(Role role);
}
