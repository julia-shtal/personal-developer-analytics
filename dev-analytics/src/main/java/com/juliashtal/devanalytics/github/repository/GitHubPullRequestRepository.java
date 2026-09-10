package com.juliashtal.devanalytics.github.repository;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.metrics.model.DailyCountProjection;
import com.juliashtal.devanalytics.metrics.model.PrLeadTimeProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for GitHubPullRequestEntity (github_pull_requests). Per-repo lookups and metric projections.
 */
public interface GitHubPullRequestRepository extends JpaRepository<GitHubPullRequestEntity, Long> {
    Optional<GitHubPullRequestEntity> findByRepositoryAndNumber(GitRepositoryEntity repository, int number);
    List<GitHubPullRequestEntity> findByRepository(GitRepositoryEntity repository);
    Page<GitHubPullRequestEntity> findByRepositoryOrderByCreatedAtDesc(GitRepositoryEntity repository, Pageable pageable);

    /**
     * Oldest pull request in the given repository scope. Used alongside the equivalent
     * commit and issue earliest-activity queries so the backfill can take the minimum of
     * all three as the start of its target range.
     */
    @Query("SELECT MIN(p.createdAt) FROM GitHubPullRequestEntity p WHERE p.repository.id IN :repoIds")
    Optional<Instant> findEarliestCreatedAt(@Param("repoIds") List<Long> repoIds);

    @Query("""
    select p.repository.id as repoId,
           p.createdAt     as createdAt,
           p.mergedAt      as mergedAt
    from GitHubPullRequestEntity p
    where p.repository.id IN :repoIds
      and p.merged = true
      and p.mergedAt between :from and :to
    """)
    List<PrLeadTimeProjection> findMergedLeadTimesByRepoIds(
            @Param("repoIds") List<Long> repoIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
    select date(p.createdAt) as day,
           p.repository.id   as repoId,
           count(p.id)       as count
    from GitHubPullRequestEntity p
    where p.repository.id IN :repoIds
      and p.createdAt between :from and :to
    group by date(p.createdAt), p.repository.id
    order by day, repoId
    """)
    List<DailyCountProjection> aggregatePrCreatedDailyByRepoIds(
            @Param("repoIds") List<Long> repoIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
    select date(p.mergedAt) as day,
           p.repository.id  as repoId,
           count(p.id)      as count
    from GitHubPullRequestEntity p
    where p.repository.id IN :repoIds
      and p.merged = true
      and p.mergedAt between :from and :to
    group by date(p.mergedAt), p.repository.id
    order by day, repoId
    """)
    List<DailyCountProjection> aggregatePrMergedDailyByRepoIds(
            @Param("repoIds") List<Long> repoIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
    select p
    from GitHubPullRequestEntity p
    where p.repository.id IN :repoIds
      and p.merged = true
      and p.mergedAt between :from and :to
    """)
    List<GitHubPullRequestEntity> findMergedPrsByRepoIds(
            @Param("repoIds") List<Long> repoIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    // -------------------------------------------------------------------------
    // Author-scoped variants: filter by explicit repo IDs + the author's numeric GitHub ID.
    //
    // Matched on the numeric ID: a login is free text, case-sensitive and re-assignable,
    // so authorLogin survives as a display value only.
    // -------------------------------------------------------------------------

    @Query("""
    select date(p.createdAt) as day,
           p.repository.id   as repoId,
           count(p.id)       as count
    from GitHubPullRequestEntity p
    where p.repository.id IN :repoIds
      and p.authorGithubId = :githubUserId
      and p.createdAt between :from and :to
    group by date(p.createdAt), p.repository.id
    order by day, repoId
    """)
    List<DailyCountProjection> aggregatePrCreatedDailyByRepoIdsAndAuthorGithubId(
            @Param("repoIds") List<Long> repoIds,
            @Param("githubUserId") Long githubUserId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
    select date(p.mergedAt) as day,
           p.repository.id  as repoId,
           count(p.id)      as count
    from GitHubPullRequestEntity p
    where p.repository.id IN :repoIds
      and p.authorGithubId = :githubUserId
      and p.merged = true
      and p.mergedAt between :from and :to
    group by date(p.mergedAt), p.repository.id
    order by day, repoId
    """)
    List<DailyCountProjection> aggregatePrMergedDailyByRepoIdsAndAuthorGithubId(
            @Param("repoIds") List<Long> repoIds,
            @Param("githubUserId") Long githubUserId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
    select p.repository.id as repoId,
           p.createdAt     as createdAt,
           p.mergedAt      as mergedAt
    from GitHubPullRequestEntity p
    where p.repository.id IN :repoIds
      and p.authorGithubId = :githubUserId
      and p.merged = true
      and p.mergedAt between :from and :to
    """)
    List<PrLeadTimeProjection> findMergedLeadTimesByRepoIdsAndAuthorGithubId(
            @Param("repoIds") List<Long> repoIds,
            @Param("githubUserId") Long githubUserId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
    select p
    from GitHubPullRequestEntity p
    where p.repository.id IN :repoIds
      and p.authorGithubId = :githubUserId
      and p.merged = true
      and p.mergedAt between :from and :to
    """)
    List<GitHubPullRequestEntity> findMergedPrsByRepoIdsAndAuthorGithubId(
            @Param("repoIds") List<Long> repoIds,
            @Param("githubUserId") Long githubUserId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Currently-open PRs (not merged, not closed) authored by the given GitHub account within the repo
     * scope. Unlike every other PR query this has no date bound: a PR opened before the reporting
     * window still counts if it is open now. Used by WipOpenPrAgeCalculator.
     */
    @Query("""
    select p
    from GitHubPullRequestEntity p
    where p.repository.id IN :repoIds
      and p.authorGithubId = :githubUserId
      and p.mergedAt is null
      and p.closedAt is null
    """)
    List<GitHubPullRequestEntity> findOpenPrsByRepoIdsAndAuthorGithubId(
            @Param("repoIds") List<Long> repoIds,
            @Param("githubUserId") Long githubUserId);

    /**
     * Returns the next batch of PRs needing stats enrichment for a specific repository,
     * newest-first so that recent PRs are always prioritised.
     */
    @Query("""
    select p from GitHubPullRequestEntity p
    where p.statsStatus = :status
      and p.repository.id = :repositoryId
    order by p.createdAt desc
    """)
    List<GitHubPullRequestEntity> findPendingPrsForRepository(
            @Param("status") StatsStatus status,
            @Param("repositoryId") Long repositoryId,
            Pageable pageable);

    /**
     * Returns distinct repository IDs that still have PRs in the given stats state.
     * Used by the background scheduler.
     */
    @Query("""
    select distinct p.repository.id from GitHubPullRequestEntity p
    where p.statsStatus = :status
    """)
    List<Long> findRepositoryIdsWithStatsStatus(@Param("status") StatsStatus status);
}

