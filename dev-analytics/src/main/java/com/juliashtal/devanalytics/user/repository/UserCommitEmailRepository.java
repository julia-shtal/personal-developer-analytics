package com.juliashtal.devanalytics.user.repository;

import com.juliashtal.devanalytics.user.model.UserCommitEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for user_commit_emails: the declared commit addresses per user.
 */
public interface UserCommitEmailRepository extends JpaRepository<UserCommitEmail, Long> {

    List<UserCommitEmail> findByUserIdOrderByEmailAsc(Long userId);

    /**
     * Looks up the owner of an address. Global rather than per-user because the address is
     * globally unique: this is the query that distinguishes "you already have it" (200) from
     * "someone else has it" (409).
     */
    Optional<UserCommitEmail> findByEmail(String email);

    Optional<UserCommitEmail> findByIdAndUserId(Long id, Long userId);

    /** Just the addresses, which is all {@link com.juliashtal.devanalytics.user.service.AuthorIdentityResolver} needs. */
    @Query("select e.email from UserCommitEmail e where e.user.id = :userId")
    List<String> findEmailsByUserId(@Param("userId") Long userId);
}
