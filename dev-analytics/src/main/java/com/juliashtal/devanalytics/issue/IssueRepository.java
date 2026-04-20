package com.juliashtal.devanalytics.issue;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.issue.model.IssueEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<IssueEntity, Long> {
    Optional<IssueEntity> findByDataSourceAndExternalId(DataSourceConfig source, String externalId);
    Page<IssueEntity> findByDataSource(DataSourceConfig source, Pageable pageable);

    // -------------------------------------------------------------------------
    // Team-repo variants: filter by explicit repo IDs, no author filter.
    // Issues are project-level; all team members share them.
    // -------------------------------------------------------------------------

    @Query("""
    select date(i.createdAt) as day,
           r.id              as repoId,
           count(i.id)       as createdCount
    from IssueEntity i
    join i.repository r
    where r.id IN :repoIds
      and i.createdAt between :from and :to
    group by date(i.createdAt), r.id
    order by day, repoId
    """)
    List<Object[]> aggregateIssuesCreatedDailyByRepoIds(
            @Param("repoIds") List<Long> repoIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
    select date(i.closedAt) as day,
           r.id             as repoId,
           count(i.id)      as closedCount
    from IssueEntity i
    join i.repository r
    where r.id IN :repoIds
      and i.closedAt is not null
      and i.closedAt between :from and :to
    group by date(i.closedAt), r.id
    order by day, repoId
    """)
    List<Object[]> aggregateIssuesClosedDailyByRepoIds(
            @Param("repoIds") List<Long> repoIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
    select r.id, i.createdAt, i.closedAt
    from IssueEntity i
    join i.repository r
    where r.id IN :repoIds
      and i.createdAt is not null
      and i.closedAt is not null
      and i.closedAt between :from and :to
    """)
    List<Object[]> findIssueLeadTimesByRepoIds(
            @Param("repoIds") List<Long> repoIds,
            @Param("from") Instant from,
            @Param("to") Instant to);
}

