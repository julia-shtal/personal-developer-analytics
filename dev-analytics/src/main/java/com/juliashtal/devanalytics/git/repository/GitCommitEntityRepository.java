package com.juliashtal.devanalytics.git.repository;

import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.metrics.model.CommitDetailProjection;
import com.juliashtal.devanalytics.metrics.model.DailyChurnProjection;
import com.juliashtal.devanalytics.metrics.model.DailyCommitsProjection;
import com.juliashtal.devanalytics.metrics.model.RepoCountProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for GitCommitEntity (git_commits). Hash lookups and per-repo commit queries.
 */
public interface GitCommitEntityRepository extends JpaRepository<GitCommitEntity, Long> {
    Optional<GitCommitEntity> findByHash(String hash);
    long countByRepositoryId(Long repositoryId);

    @Query("select c.hash from GitCommitEntity c where c.repository.id = :repositoryId")
    List<String> findHashesByRepositoryId(@Param("repositoryId") Long repositoryId);
    Page<GitCommitEntity> findByRepositoryIdOrderByAuthorDateDesc(Long repositoryId, Pageable pageable);

    /**
     * Oldest commit in the given repository scope. The backfill uses this — not sync_jobs —
     * as its coverage reference, because it is the same table the calculators read: history
     * that was collected but never calculated is exactly what the backfill has to find.
     */
    @Query("SELECT MIN(c.authorDate) FROM GitCommitEntity c WHERE c.repository.id IN :repoIds")
    Optional<Instant> findEarliestAuthorDate(@Param("repoIds") List<Long> repoIds);

    @Query("""
    select date(c.authorDate) as day,
           r.id               as repoId,
           sum(c.additions)   as additions,
           sum(c.deletions)   as deletions
    from GitCommitEntity c
    join c.repository r
    where r.id IN :repoIds
      and c.authorDate between :from and :to
    group by date(c.authorDate), r.id
    order by day, repoId
    """)
    List<DailyChurnProjection> aggregateChurnDailyByRepoIds(
            @Param("repoIds") List<Long> repoIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
    select c
    from GitCommitEntity c
    where c.repository = :repository
      and c.message like concat('%#', :prNumber, '%')
    order by c.authorDate asc
    """)
    List<GitCommitEntity> findCommitsForPr(GitRepositoryEntity repository, int prNumber);

    @Query("""
    select date(c.authorDate) as day,
           r.id               as repoId,
           count(c.id)        as commitsCount,
           avg(c.additions + c.deletions) as avgSize
    from GitCommitEntity c
    join c.repository r
    where r.id IN :repoIds
      and c.authorDate between :from and :to
    group by date(c.authorDate), r.id
    order by day, repoId
    """)
    List<DailyCommitsProjection> aggregateCommitsDailyByRepoIds(
            @Param("repoIds") List<Long> repoIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    // -------------------------------------------------------------------------
    // Team-repo variants: filter by explicit repo IDs + commit author email
    // Used when a data source is team-scoped (ds.user_id = manager, not member).
    // -------------------------------------------------------------------------

    @Query("""
    select date(c.authorDate) as day,
           r.id               as repoId,
           count(c.id)        as commitsCount,
           avg(c.additions + c.deletions) as avgSize
    from GitCommitEntity c
    join c.repository r
    where r.id IN :repoIds
      and c.authorEmail = :authorEmail
      and c.authorDate between :from and :to
    group by date(c.authorDate), r.id
    order by day, repoId
    """)
    List<DailyCommitsProjection> aggregateCommitsDailyByRepoIdsAndAuthorEmail(
            @Param("repoIds") List<Long> repoIds,
            @Param("authorEmail") String authorEmail,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
    select date(c.authorDate) as day,
           r.id               as repoId,
           sum(c.additions)   as additions,
           sum(c.deletions)   as deletions
    from GitCommitEntity c
    join c.repository r
    where r.id IN :repoIds
      and c.authorEmail = :authorEmail
      and c.authorDate between :from and :to
    group by date(c.authorDate), r.id
    order by day, repoId
    """)
    List<DailyChurnProjection> aggregateChurnDailyByRepoIdsAndAuthorEmail(
            @Param("repoIds") List<Long> repoIds,
            @Param("authorEmail") String authorEmail,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
    select c
    from GitCommitEntity c
    join c.repository r
    where r.id IN :repoIds
      and c.message like concat('%#', :prNumber, '%')
    order by c.authorDate asc
    """)
    List<GitCommitEntity> findCommitsForPrByRepoIds(
            @Param("repoIds") List<Long> repoIds,
            @Param("prNumber") int prNumber);

    /**
     * Returns (authorDate, additions, deletions, statsStatus) for all commits by one author in a window.
     * Used by after-hours ratio and refactor ratio calculations.
     * statsStatus is included so callers can exclude PENDING/FAILED commits from metrics
     * that depend on real diff stats (additions/deletions).
     */
    @Query("""
    select c.authorDate   as authorDate,
           c.additions    as additions,
           c.deletions    as deletions,
           c.statsStatus  as statsStatus
    from GitCommitEntity c
    where c.repository.id IN :repoIds
      and c.authorEmail = :authorEmail
      and c.authorDate between :from and :to
    """)
    List<CommitDetailProjection> findCommitDetailsByRepoIdsAndAuthorEmail(
            @Param("repoIds") List<Long> repoIds,
            @Param("authorEmail") String authorEmail,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Total commit count per repo (all authors) in a window.
     * Used as the denominator for knowledge silo score.
     */
    @Query("""
    select c.repository.id as repoId,
           count(c.id)     as count
    from GitCommitEntity c
    where c.repository.id IN :repoIds
      and c.authorDate between :from and :to
    group by c.repository.id
    """)
    List<RepoCountProjection> countTotalCommitsByRepoIds(
            @Param("repoIds") List<Long> repoIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Commit count per repo for one specific author in a window.
     * Used as the numerator for knowledge silo score.
     */
    @Query("""
    select c.repository.id as repoId,
           count(c.id)     as count
    from GitCommitEntity c
    where c.repository.id IN :repoIds
      and c.authorEmail = :authorEmail
      and c.authorDate between :from and :to
    group by c.repository.id
    """)
    List<RepoCountProjection> countCommitsByRepoIdsAndAuthorEmail(
            @Param("repoIds") List<Long> repoIds,
            @Param("authorEmail") String authorEmail,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Returns the next batch of commits whose stats have not yet been enriched,
     * ordered newest-first so that recent commits are always prioritised.
     * Used by the background enrichment scheduler.
     */
    @Query("""
    select c from GitCommitEntity c
    where c.statsStatus = :status
    order by c.authorDate desc
    """)
    List<GitCommitEntity> findPendingCommitsForEnrichment(
            @Param("status") StatsStatus status,
            Pageable pageable);

    /**
     * Returns distinct repository IDs that still have commits in the given stats state.
     * Used by the background scheduler to find which repos need enrichment.
     */
    @Query("""
    select distinct c.repository.id from GitCommitEntity c
    where c.statsStatus = :status
    """)
    List<Long> findRepositoryIdsWithStatsStatus(@Param("status") StatsStatus status);

    /**
     * Returns the next batch of PENDING commits for a specific repository,
     * ordered newest-first.
     */
    @Query("""
    select c from GitCommitEntity c
    where c.statsStatus = :status
      and c.repository.id = :repositoryId
    order by c.authorDate desc
    """)
    List<GitCommitEntity> findPendingCommitsForRepository(
            @Param("status") StatsStatus status,
            @Param("repositoryId") Long repositoryId,
            Pageable pageable);
}

