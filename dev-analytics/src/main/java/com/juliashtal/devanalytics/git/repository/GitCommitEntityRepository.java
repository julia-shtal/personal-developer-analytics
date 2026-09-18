package com.juliashtal.devanalytics.git.repository;

import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.metrics.model.CommitDetailProjection;
import com.juliashtal.devanalytics.metrics.model.DailyChurnProjection;
import com.juliashtal.devanalytics.metrics.model.DailyCommitsProjection;
import com.juliashtal.devanalytics.metrics.model.RepoCountProjection;
import com.juliashtal.devanalytics.metrics.model.StatsCoverageProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
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
     * Oldest commit in the given repository scope. The backfill uses this rather than
     * {@code sync_jobs}, because it is the same table the calculators read.
     */
    @Query("SELECT MIN(c.authorDate) FROM GitCommitEntity c WHERE c.repository.id IN :repoIds")
    Optional<Instant> findEarliestAuthorDate(@Param("repoIds") List<Long> repoIds);

    @Query("""
    select c
    from GitCommitEntity c
    where c.repository = :repository
      and c.message like concat('%#', :prNumber, '%')
    order by c.authorDate asc
    """)
    List<GitCommitEntity> findCommitsForPr(GitRepositoryEntity repository, int prNumber);

    // -------------------------------------------------------------------------
    // Author-scoped variants: filter by explicit repo IDs + the user's AuthorIdentity.
    //
    // The predicate is a disjunction because neither identifier covers every commit:
    // author_github_id is null for local JGit commits, and a declared address misses commits
    // made with an address the user never registered. Both branches select the same row when
    // both match, so a commit is never double-counted, and a null githubUserId simply makes
    // its branch unsatisfiable so the email set carries the match alone.
    //
    // The bot filter is needed because of that email branch: an automation account configured
    // with the user's own address satisfies it. No null guard is needed on author_name; the
    // column is NOT NULL.
    //
    // Windows are half-open: `from` inclusive, `to` exclusive, so adjacent windows neither
    // double-count a record nor drop one. Callers pass toDate + 1 at midnight UTC.
    // -------------------------------------------------------------------------

    /**
     * Daily commit count and average changed lines per commit, for one author in a window.
     *
     * <p>The two figures are drawn from different populations on purpose. The count is over
     * every attributed commit, because counting a commit needs nothing but its date. The
     * average is over enriched commits only: an unenriched commit carries additions and
     * deletions of zero as a placeholder, and averaging that in would fabricate an
     * observation rather than omit one. Non-enriched rows are narrowed to null inside the
     * aggregate rather than by the where clause, so the count keeps its own population and
     * one round-trip still serves both metrics.</p>
     */
    @Query("""
    select date(c.authorDate) as day,
           r.id               as repoId,
           count(c.id)        as commitsCount,
           avg(case when c.statsStatus = com.juliashtal.devanalytics.git.model.StatsStatus.COMPLETE
                    then c.additions + c.deletions end) as avgSize
    from GitCommitEntity c
    join c.repository r
    where r.id IN :repoIds
      and (c.authorGithubId = :githubUserId or lower(c.authorEmail) in :emails)
      and c.authorName not like '%[bot]%'
      and c.authorDate >= :from
      and c.authorDate  < :to
    group by date(c.authorDate), r.id
    order by day, repoId
    """)
    List<DailyCommitsProjection> aggregateCommitsDailyByRepoIdsAndIdentity(
            @Param("repoIds") List<Long> repoIds,
            @Param("githubUserId") Long githubUserId,
            @Param("emails") Collection<String> emails,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Daily added and deleted line totals for one author in a window, over enriched commits.
     *
     * <p>Restricted to {@code COMPLETE} in the where clause rather than inside the sums,
     * because the churn ratio needs the day to disappear when nothing on it was enriched:
     * summing placeholder zeros would otherwise store a ratio of 0.0 that reads as an
     * all-additions day. A day with enriched commits whose diffs are genuinely empty still
     * produces a row, and the calculator's denominator guard stores 0.0 for it.</p>
     */
    @Query("""
    select date(c.authorDate) as day,
           r.id               as repoId,
           sum(c.additions)   as additions,
           sum(c.deletions)   as deletions
    from GitCommitEntity c
    join c.repository r
    where r.id IN :repoIds
      and (c.authorGithubId = :githubUserId or lower(c.authorEmail) in :emails)
      and c.authorName not like '%[bot]%'
      and c.statsStatus = com.juliashtal.devanalytics.git.model.StatsStatus.COMPLETE
      and c.authorDate >= :from
      and c.authorDate  < :to
    group by date(c.authorDate), r.id
    order by day, repoId
    """)
    List<DailyChurnProjection> aggregateChurnDailyByRepoIdsAndIdentity(
            @Param("repoIds") List<Long> repoIds,
            @Param("githubUserId") Long githubUserId,
            @Param("emails") Collection<String> emails,
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
      and (c.authorGithubId = :githubUserId or lower(c.authorEmail) in :emails)
      and c.authorName not like '%[bot]%'
      and c.authorDate >= :from
      and c.authorDate  < :to
    """)
    List<CommitDetailProjection> findCommitDetailsByRepoIdsAndIdentity(
            @Param("repoIds") List<Long> repoIds,
            @Param("githubUserId") Long githubUserId,
            @Param("emails") Collection<String> emails,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Commit count per repo for every human author in a window.
     * Used as the denominator for knowledge silo score.
     */
    @Query("""
    select c.repository.id as repoId,
           count(c.id)     as count
    from GitCommitEntity c
    where c.repository.id IN :repoIds
      and c.authorName not like '%[bot]%'
      and c.authorDate >= :from
      and c.authorDate  < :to
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
      and (c.authorGithubId = :githubUserId or lower(c.authorEmail) in :emails)
      and c.authorName not like '%[bot]%'
      and c.authorDate >= :from
      and c.authorDate  < :to
    group by c.repository.id
    """)
    List<RepoCountProjection> countCommitsByRepoIdsAndIdentity(
            @Param("repoIds") List<Long> repoIds,
            @Param("githubUserId") Long githubUserId,
            @Param("emails") Collection<String> emails,
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

    /**
     * Enrichment state of every commit in the window, grouped by status and skip reason.
     *
     * <p>Window bounds match the metric calculators' so the counts describe the same population
     * the line-count metrics were computed over.</p>
     */
    @Query("""
    select c.statsStatus     as statsStatus,
           c.statsSkipReason as statsSkipReason,
           count(c)          as recordCount
    from GitCommitEntity c
    where c.repository.id in :repoIds
      and c.authorDate >= :from
      and c.authorDate  < :to
    group by c.statsStatus, c.statsSkipReason
    """)
    List<StatsCoverageProjection> countByStatsStateInRange(
            @Param("repoIds") List<Long> repoIds,
            @Param("from") Instant from,
            @Param("to") Instant to);
}

