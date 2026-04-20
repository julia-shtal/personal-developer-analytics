package com.juliashtal.devanalytics.git.repository;

import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GitCommitEntityRepository extends JpaRepository<GitCommitEntity, Long> {
    Optional<GitCommitEntity> findByHash(String hash);
    long countByRepositoryId(Long repositoryId);
    Page<GitCommitEntity> findByRepositoryIdOrderByAuthorDateDesc(Long repositoryId, Pageable pageable);

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
    List<Object[]> aggregateChurnDailyByRepoIds(
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
    List<Object[]> aggregateCommitsDailyByRepoIds(
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
    List<Object[]> aggregateCommitsDailyByRepoIdsAndAuthorEmail(
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
    List<Object[]> aggregateChurnDailyByRepoIdsAndAuthorEmail(
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
}

