package com.juliashtal.devanalytics.git.repository;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GitRepositoryEntityRepository extends JpaRepository<GitRepositoryEntity, Long> {
    List<GitRepositoryEntity> findAllByDataSourceConfig(DataSourceConfig dataSourceConfig);
    long countByDataSourceConfig(DataSourceConfig dataSourceConfig);
    Optional<GitRepositoryEntity> findByDataSourceConfigAndName(
            DataSourceConfig cfg, String name
    );
    Optional<GitRepositoryEntity> findByRepoFullName(String repoFullName);

    @Query("SELECT r.id FROM GitRepositoryEntity r WHERE r.dataSourceConfig.team.id IN :teamIds")
    List<Long> findIdsByTeamIds(@Param("teamIds") List<Long> teamIds);

    @Query("SELECT r FROM GitRepositoryEntity r JOIN FETCH r.dataSourceConfig WHERE r.id = :id")
    Optional<GitRepositoryEntity> findByIdWithDataSourceConfig(@Param("id") Long id);

    /**
     * Loads repos by IDs with their data source eagerly fetched.
     * Used to include subscribed repos that live under another user's data source.
     */
    @Query("SELECT r FROM GitRepositoryEntity r JOIN FETCH r.dataSourceConfig WHERE r.id IN :ids")
    List<GitRepositoryEntity> findAllByIdWithDataSourceConfig(@Param("ids") Collection<Long> ids);

    // ── user_accessible_repos view queries (T4.3) ─────────────────────────────

    /** All repo IDs the user can access across owned, subscribed, and team paths. */
    @Query(value = "SELECT DISTINCT repo_id FROM user_accessible_repos WHERE user_id = :userId",
           nativeQuery = true)
    List<Long> findAccessibleRepoIds(@Param("userId") Long userId);

    /** Repo IDs accessible to the user that belong to a specific datasource. */
    @Query(value = """
           SELECT DISTINCT repo_id FROM user_accessible_repos
           WHERE user_id = :userId AND data_source_id = :dataSourceId
           """, nativeQuery = true)
    List<Long> findAccessibleRepoIdsByDataSource(@Param("userId") Long userId,
                                                 @Param("dataSourceId") Long dataSourceId);
}

