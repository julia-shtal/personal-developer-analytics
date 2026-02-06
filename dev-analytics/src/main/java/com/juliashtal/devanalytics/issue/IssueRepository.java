package com.juliashtal.devanalytics.issue;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IssueRepository extends JpaRepository<IssueEntity, Long> {
    Optional<IssueEntity> findByDataSourceAndExternalId(DataSourceConfig source, String externalId);
    Page<IssueEntity> findByDataSource(DataSourceConfig source, Pageable pageable);
}

