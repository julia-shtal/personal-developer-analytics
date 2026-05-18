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
}

