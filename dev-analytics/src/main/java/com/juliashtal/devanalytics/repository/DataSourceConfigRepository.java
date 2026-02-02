package com.juliashtal.devanalytics.repository;

import com.juliashtal.devanalytics.model.DataSourceConfig;
import com.juliashtal.devanalytics.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DataSourceConfigRepository extends JpaRepository<DataSourceConfig, Long> {
    List<DataSourceConfig> findAllByUser(User user);
    Optional<DataSourceConfig> findByIdAndUser(Long id, User user);
}

