package com.juliashtal.devanalytics.issue;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.issue.model.IssueEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<IssueEntity, Long> {
    Optional<IssueEntity> findByDataSourceAndExternalId(DataSourceConfig source, String externalId);
    Page<IssueEntity> findByDataSource(DataSourceConfig source, Pageable pageable);

    @Query("""
    select r.id, i.createdAt, i.closedAt
    from IssueEntity i
    join i.dataSource ds
    join ds.user u
    left join i.repository r
    where u.id = :userId
      and r.id is not null
      and i.createdAt is not null
      and i.closedAt is not null
      and i.closedAt between :from and :to
    """)
    List<Object[]> findIssueLeadTimesPerRepo(Long userId, Instant from, Instant to);


    @Query("""
    select date(i.createdAt) as day,
           r.id              as repoId,
           count(i.id)       as createdCount
    from IssueEntity i
    join i.dataSource ds
    join ds.user u
    left join i.repository r
    where u.id = :userId
      and i.createdAt between :from and :to
      and r.id is not null
    group by date(i.createdAt), r.id
    order by day, repoId
    """)
    List<Object[]> aggregateIssuesCreatedDailyPerRepo(Long userId, Instant from, Instant to);

    @Query("""
    select date(i.closedAt) as day,
           r.id              as repoId,
           count(i.id)       as closedCount
    from IssueEntity i
    join i.dataSource ds
    join ds.user u
    left join i.repository r
    where u.id = :userId
      and i.closedAt is not null
      and i.closedAt between :from and :to
      and r.id is not null
    group by date(i.closedAt), r.id
    order by day, repoId
    """)
    List<Object[]> aggregateIssuesClosedDailyPerRepo(Long userId, Instant from, Instant to);

    @Query("""
        select date(i.createdAt) as day,
               count(i.id)       as createdCount
        from IssueEntity i
        join i.dataSource ds
        join ds.user u
        where u.id = :userId
          and i.createdAt between :from and :to
        group by date(i.createdAt)
        order by day
        """)
    List<Object[]> aggregateIssuesCreatedDaily(Long userId, Instant from, Instant to);

    @Query("""
        select date(i.closedAt) as day,
               count(i.id)      as closedCount
        from IssueEntity i
        join i.dataSource ds
        join ds.user u
        where u.id = :userId
          and i.closedAt is not null
          and i.closedAt between :from and :to
        group by date(i.closedAt)
        order by day
        """)
    List<Object[]> aggregateIssuesClosedDaily(Long userId, Instant from, Instant to);

    @Query("""
        select i.createdAt, i.closedAt
        from IssueEntity i
        join i.dataSource ds
        join ds.user u
        where u.id = :userId
          and i.createdAt is not null
          and i.closedAt is not null
          and i.closedAt between :from and :to
        """)
    List<Object[]> findIssueLeadTimes(Long userId, Instant from, Instant to);
}

