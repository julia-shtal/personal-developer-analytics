package com.juliashtal.devanalytics.jira.repository;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.service.JiraProjectService;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for JiraProjectEntity (jira_projects). Canonical lookups by normalized base URL and key.
 */
public interface JiraProjectRepository extends JpaRepository<JiraProjectEntity, Long> {

    // EntityGraph ensures dataSource is initialized before the transaction closes,
    // so callers outside a session (e.g. DataSourceCollectService) can access it safely.
    @EntityGraph(attributePaths = "dataSource")
    List<JiraProjectEntity> findAllByDataSource(DataSourceConfig dataSource);

    Optional<JiraProjectEntity> findByDataSourceAndProjectKey(DataSourceConfig dataSource, String projectKey);

    @Query("SELECT j.id FROM JiraProjectEntity j WHERE j.dataSource.id IN :dataSourceIds")
    List<Long> findIdsByDataSourceIds(@Param("dataSourceIds") List<Long> dataSourceIds);

    /**
     * Global canonical-row lookup by normalized base URL + project key.
     * Used by {@link JiraProjectService#addProject} to detect cross-DS duplicates before insert,
     * mirroring the git_repositories.repo_full_name UNIQUE constraint.
     */
    Optional<JiraProjectEntity> findByBaseUrlNormalizedAndProjectKey(String baseUrlNormalized, String projectKey);

    /**
     * Returns all tracked projects for a given Jira instance (normalized base URL).
     * Used by {@link JiraProjectService#findProjectsByBaseUrl} to detect existing Jira DSes
     * before creating a duplicate during datasource creation.
     */
    @EntityGraph(attributePaths = "dataSource")
    List<JiraProjectEntity> findAllByBaseUrlNormalized(String baseUrlNormalized);
}
