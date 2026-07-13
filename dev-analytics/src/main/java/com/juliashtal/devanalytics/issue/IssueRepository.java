package com.juliashtal.devanalytics.issue;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.issue.model.IssueEntity;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.metrics.model.DailyCountProjection;
import com.juliashtal.devanalytics.metrics.model.IssueLeadTimeProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for IssueEntity (issues). Unified GitHub and Jira issue lookups.
 */
public interface IssueRepository extends JpaRepository<IssueEntity, Long> {

    // GitHub issue lookups (data_source_id not null)
    Optional<IssueEntity> findByDataSourceAndSourceIssueKey(DataSourceConfig source, String sourceIssueKey);
    Page<IssueEntity> findByDataSource(DataSourceConfig source, Pageable pageable);
    Page<IssueEntity> findByRepository(GitRepositoryEntity repository, Pageable pageable);

    // Jira issue lookups (jira_project_id not null)
    Optional<IssueEntity> findByJiraProjectAndSourceIssueKey(JiraProjectEntity project, String sourceIssueKey);
    Page<IssueEntity> findByJiraProject(JiraProjectEntity project, Pageable pageable);

    long countByRepository_IdAndState(Long repositoryId, String state);
    long countByJiraProject_Id(Long projectId);
    long countByJiraProject_IdAndClosedAtIsNotNull(Long projectId);

    // -------------------------------------------------------------------------
    // Team-repo variants: filter by explicit repo IDs, no author filter.
    // Issues are project-level; all team members share them.
    //
    // COALESCE(i.repository_id, rm.repository_id) routes both
    // GitHub issues (direct repository_id FK) and Jira issues (via
    // jira_project_repo_mappings) through the same repo-scoped aggregation.
    // Native SQL is used because JPQL cannot express the LEFT JOIN + COALESCE
    // pattern across nullable FKs from two different sources cleanly.
    // -------------------------------------------------------------------------

    @Query(value = """
    SELECT date(i.created_at)                                        AS day,
           COALESCE(i.repository_id, rm.repository_id)              AS repoId,
           count(i.id)                                               AS count
    FROM   issues i
    LEFT   JOIN jira_project_repo_mappings rm ON rm.jira_project_id = i.jira_project_id
    WHERE  COALESCE(i.repository_id, rm.repository_id) IN (:repoIds)
      AND  i.created_at BETWEEN :from AND :to
    GROUP  BY date(i.created_at), COALESCE(i.repository_id, rm.repository_id)
    ORDER  BY day, repoId
    """, nativeQuery = true)
    List<DailyCountProjection> aggregateIssuesCreatedDailyByRepoIds(
            @Param("repoIds") List<Long> repoIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query(value = """
    SELECT date(i.closed_at)                                         AS day,
           COALESCE(i.repository_id, rm.repository_id)              AS repoId,
           count(i.id)                                               AS count
    FROM   issues i
    LEFT   JOIN jira_project_repo_mappings rm ON rm.jira_project_id = i.jira_project_id
    WHERE  COALESCE(i.repository_id, rm.repository_id) IN (:repoIds)
      AND  i.closed_at IS NOT NULL
      AND  i.closed_at BETWEEN :from AND :to
    GROUP  BY date(i.closed_at), COALESCE(i.repository_id, rm.repository_id)
    ORDER  BY day, repoId
    """, nativeQuery = true)
    List<DailyCountProjection> aggregateIssuesClosedDailyByRepoIds(
            @Param("repoIds") List<Long> repoIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query(value = """
    SELECT COALESCE(i.repository_id, rm.repository_id) AS repoId,
           i.created_at                                AS createdAt,
           i.closed_at                                 AS closedAt
    FROM   issues i
    LEFT   JOIN jira_project_repo_mappings rm ON rm.jira_project_id = i.jira_project_id
    WHERE  COALESCE(i.repository_id, rm.repository_id) IN (:repoIds)
      AND  i.created_at IS NOT NULL
      AND  i.closed_at IS NOT NULL
      AND  i.closed_at BETWEEN :from AND :to
    """, nativeQuery = true)
    List<IssueLeadTimeProjection> findIssueLeadTimesByRepoIds(
            @Param("repoIds") List<Long> repoIds,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
