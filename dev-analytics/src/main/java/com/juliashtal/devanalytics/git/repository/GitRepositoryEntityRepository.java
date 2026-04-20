package com.juliashtal.devanalytics.git.repository;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GitRepositoryEntityRepository extends JpaRepository<GitRepositoryEntity, Long> {
    List<GitRepositoryEntity> findAllByDataSourceConfig(DataSourceConfig dataSourceConfig);
    Optional<GitRepositoryEntity> findByDataSourceConfigAndName(
            DataSourceConfig cfg, String name
    );
    Optional<GitRepositoryEntity> findByRepoFullName(String repoFullName);

    @Query("SELECT r.id FROM GitRepositoryEntity r WHERE r.dataSourceConfig.team.id IN :teamIds")
    List<Long> findIdsByTeamIds(@Param("teamIds") List<Long> teamIds);
}

