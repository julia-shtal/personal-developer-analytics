package com.juliashtal.devanalytics.git.repository;

import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GitCommitEntityRepository extends JpaRepository<GitCommitEntity, Long> {
    Optional<GitCommitEntity> findByHash(String hash);
    long countByRepositoryId(Long repositoryId);
    Page<GitCommitEntity> findByRepositoryIdOrderByAuthorDateDesc(Long repositoryId, Pageable pageable);

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
    join r.dataSourceConfig ds
    join ds.user u
    where u.id = :userId
      and c.authorDate between :from and :to
    group by date(c.authorDate), r.id
    order by day, repoId
    """)
    List<Object[]> aggregateCommitsDailyPerRepo(Long userId, Instant from, Instant to);

    @Query("""
        select date(c.authorDate) as day,
               count(c.id)        as commitsCount,
               percentile_cont(0.5) within group (order by (c.additions + c.deletions)) as medianSize
        from GitCommitEntity c
        join c.repository r
        join r.dataSourceConfig ds
        join ds.user u
        where u.id = :userId
          and c.authorDate between :from and :to
        group by date(c.authorDate)
        order by day
        """)
    List<Object[]> aggregateCommitsDaily(Long userId, Instant from, Instant to);

    @Query("""
    select date(c.authorDate) as day,
           sum(c.additions)   as additions,
           sum(c.deletions)   as deletions
    from GitCommitEntity c
    join c.repository r
    join r.dataSourceConfig ds
    join ds.user u
    where u.id = :userId
      and c.authorDate between :from and :to
    group by date(c.authorDate)
    order by day
    """)
    List<Object[]> aggregateChurnDaily(Long userId, Instant from, Instant to);

}

