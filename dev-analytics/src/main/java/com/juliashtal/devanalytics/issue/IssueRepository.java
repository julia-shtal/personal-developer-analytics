package com.juliashtal.devanalytics.issue;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.issue.model.IssueEntity;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<IssueEntity, Long> {

    // GitHub issue lookups (data_source_id not null)
    Optional<IssueEntity> findByDataSourceAndExternalId(DataSourceConfig source, String externalId);
    Page<IssueEntity> findByDataSource(DataSourceConfig source, Pageable pageable);
    Page<IssueEntity> findByRepository(GitRepositoryEntity repository, Pageable pageable);

    // Jira issue lookups (jira_project_id not null)
    Optional<IssueEntity> findByJiraProjectAndExternalId(JiraProjectEntity project, String externalId);
    Page<IssueEntity> findByJiraProject(JiraProjectEntity project, Pageable pageable);

    long countByRepository_IdAndState(Long repositoryId, String state);
    long countByJiraProject_Id(Long projectId);
    long countByJiraProject_IdAndClosedAtIsNotNull(Long projectId);

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
