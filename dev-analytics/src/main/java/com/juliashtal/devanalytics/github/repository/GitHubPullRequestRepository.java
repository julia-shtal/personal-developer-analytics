package com.juliashtal.devanalytics.github.repository;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
      join p.repository r
      join r.dataSourceConfig ds
      join ds.user u
      where u.id = :userId
        and p.merged = true
        and p.mergedAt between :from and :to
  """)
    List<Object[]> findMergedLeadTimesPerRepo(Long userId, Instant from, Instant to);

    @Query("""
  select date(p.createdAt) as day,
         r.id             as repoId,
         count(p.id)      as createdCount
  from GitHubPullRequestEntity p
  join p.repository r
  join r.dataSourceConfig ds
  join ds.user u
  where u.id = :userId
    and p.createdAt between :from and :to
  group by date(p.createdAt), r.id
  order by day, repoId
  """)
    List<Object[]> aggregatePrCreatedDailyPerRepo(Long userId, Instant from, Instant to);

    @Query("""
  select date(p.mergedAt) as day,
         r.id             as repoId,
         count(p.id)      as mergedCount
  from GitHubPullRequestEntity p
  join p.repository r
  join r.dataSourceConfig ds
  join ds.user u
  where u.id = :userId
    and p.merged = true
    and p.mergedAt between :from and :to
  group by date(p.mergedAt), r.id
  order by day, repoId
  """)
    List<Object[]> aggregatePrMergedDailyPerRepo(Long userId, Instant from, Instant to);


    @Query("""
    select p
    from GitHubPullRequestEntity p
    join p.repository r
    join r.dataSourceConfig ds
    join ds.user u
    where u.id = :userId
      and p.merged = true
      and p.mergedAt between :from and :to
    """)
    List<GitHubPullRequestEntity> findMergedPrsForLeadTime(Long userId, Instant from, Instant to);

    @Query("""
        select date(p.createdAt) as day,
               count(p.id)       as createdCount
        from GitHubPullRequestEntity p
        join p.repository r
        join r.dataSourceConfig ds
        join ds.user u
        where u.id = :userId
          and p.createdAt between :from and :to
        group by date(p.createdAt)
        order by day
        """)
    List<Object[]> aggregatePrCreatedDaily(Long userId, Instant from, Instant to);

    @Query("""
        select date(p.mergedAt) as day,
               count(p.id)      as mergedCount
        from GitHubPullRequestEntity p
        join p.repository r
        join r.dataSourceConfig ds
        join ds.user u
        where u.id = :userId
          and p.merged = true
          and p.mergedAt between :from and :to
        group by date(p.mergedAt)
        order by day
        """)
    List<Object[]> aggregatePrMergedDaily(Long userId, Instant from, Instant to);

    // lead time: createdAt -> mergedAt (в часах)
    @Query("""
        select p.createdAt, p.mergedAt
        from GitHubPullRequestEntity p
        join p.repository r
        join r.dataSourceConfig ds
        join ds.user u
        where u.id = :userId
          and p.merged = true
          and p.mergedAt between :from and :to
        """)
    List<Object[]> findMergedLeadTimes(Long userId, Instant from, Instant to);
}

