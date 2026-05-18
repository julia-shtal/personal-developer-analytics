package com.juliashtal.devanalytics.jira;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JiraProjectRepository extends JpaRepository<JiraProjectEntity, Long> {
    // EntityGraph ensures dataSource is initialized before the transaction closes,
    // so callers outside a session (e.g. DataSourceCollectService) can access it safely.
    @EntityGraph(attributePaths = "dataSource")
    List<JiraProjectEntity> findAllByDataSource(DataSourceConfig dataSource);
    Optional<JiraProjectEntity> findByDataSourceAndProjectKey(DataSourceConfig dataSource, String projectKey);

    @Query("SELECT j.id FROM JiraProjectEntity j WHERE j.dataSource.id IN :dataSourceIds")
    List<Long> findIdsByDataSourceIds(@Param("dataSourceIds") List<Long> dataSourceIds);

    /** Lookup by Jira instance URL + project key — used to detect existing projects when a new Jira DS is created. */
    Optional<JiraProjectEntity> findByDataSource_BaseUrlAndProjectKey(String baseUrl, String projectKey);
}
