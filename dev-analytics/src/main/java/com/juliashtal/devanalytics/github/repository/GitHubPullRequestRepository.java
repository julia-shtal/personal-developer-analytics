package com.juliashtal.devanalytics.github.repository;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GitHubPullRequestRepository extends JpaRepository<GitHubPullRequestEntity, Long> {
    Optional<GitHubPullRequestEntity> findByRepositoryAndNumber(GitRepositoryEntity repository, int number);
    Page<GitHubPullRequestEntity> findByRepositoryOrderByCreatedAtDesc(GitRepositoryEntity repository, Pageable pageable);

    @Query("""
    select p.repository.id as repoId,
           p.createdAt,
           p.mergedAt
    from GitHubPullRequestEntity p
    where p.repository.id IN :repoIds
      and p.merged = true
      and p.mergedAt between :from and :to
    """)
    List<Object[]> findMergedLeadTimesByRepoIds(
            @Param("repoIds") List<Long> repoIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
    select date(p.createdAt) as day,
           p.repository.id   as repoId,
           count(p.id)       as createdCount
    from GitHubPullRequestEntity p
    where p.repository.id IN :repoIds
      and p.createdAt between :from and :to
    group by date(p.createdAt), p.repository.id
    order by day, repoId
    """)
    List<Object[]> aggregatePrCreatedDailyByRepoIds(
            @Param("repoIds") List<Long> repoIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
    select date(p.mergedAt) as day,
           p.repository.id  as repoId,
           count(p.id)      as mergedCount
    from GitHubPullRequestEntity p
    where p.repository.id IN :repoIds
      and p.merged = true
      and p.mergedAt between :from and :to
    group by date(p.mergedAt), p.repository.id
    order by day, repoId
    """)
    List<Object[]> aggregatePrMergedDailyByRepoIds(
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
    // Team-repo variants: filter by explicit repo IDs + PR author login
    // -------------------------------------------------------------------------

    @Query("""
    select date(p.createdAt) as day,
           p.repository.id   as repoId,
           count(p.id)        as createdCount
    from GitHubPullRequestEntity p
    where p.repository.id IN :repoIds
      and p.authorLogin = :authorLogin
      and p.createdAt between :from and :to
    group by date(p.createdAt), p.repository.id
    order by day, repoId
    """)
    List<Object[]> aggregatePrCreatedDailyByRepoIdsAndAuthorLogin(
            @Param("repoIds") List<Long> repoIds,
            @Param("authorLogin") String authorLogin,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
    select date(p.mergedAt) as day,
           p.repository.id  as repoId,
           count(p.id)       as mergedCount
    from GitHubPullRequestEntity p
    where p.repository.id IN :repoIds
      and p.authorLogin = :authorLogin
      and p.merged = true
      and p.mergedAt between :from and :to
    group by date(p.mergedAt), p.repository.id
    order by day, repoId
    """)
    List<Object[]> aggregatePrMergedDailyByRepoIdsAndAuthorLogin(
            @Param("repoIds") List<Long> repoIds,
            @Param("authorLogin") String authorLogin,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
    select p.repository.id as repoId,
           p.createdAt,
           p.mergedAt
    from GitHubPullRequestEntity p
    where p.repository.id IN :repoIds
      and p.authorLogin = :authorLogin
      and p.merged = true
      and p.mergedAt between :from and :to
    """)
    List<Object[]> findMergedLeadTimesByRepoIdsAndAuthorLogin(
            @Param("repoIds") List<Long> repoIds,
            @Param("authorLogin") String authorLogin,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
    select p
    from GitHubPullRequestEntity p
    where p.repository.id IN :repoIds
      and p.authorLogin = :authorLogin
      and p.merged = true
      and p.mergedAt between :from and :to
    """)
    List<GitHubPullRequestEntity> findMergedPrsByRepoIdsAndAuthorLogin(
            @Param("repoIds") List<Long> repoIds,
            @Param("authorLogin") String authorLogin,
            @Param("from") Instant from,
            @Param("to") Instant to);
}

