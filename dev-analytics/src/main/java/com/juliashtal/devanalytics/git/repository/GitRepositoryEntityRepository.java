package com.juliashtal.devanalytics.git.repository;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GitRepositoryEntityRepository extends JpaRepository<GitRepositoryEntity, Long> {
    List<GitRepositoryEntity> findAllByDataSourceConfig(DataSourceConfig dataSourceConfig);
}

