package com.juliashtal.devanalytics.repository;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DataSourceConfigRepository extends JpaRepository<DataSourceConfig, Long> {
    List<DataSourceConfig> findAllByUser(User user);
    Optional<DataSourceConfig> findByIdAndUser(Long id, User user);
}

