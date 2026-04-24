package com.juliashtal.devanalytics.datasource.repository;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DataSourceConfigRepository extends JpaRepository<DataSourceConfig, Long> {
    List<DataSourceConfig> findAllByUser(User user);
    List<DataSourceConfig> findAllByUserAndTeamIsNull(User user);
    Optional<DataSourceConfig> findByIdAndUser(Long id, User user);

    List<DataSourceConfig> findAllByTeam(Team team);
    Optional<DataSourceConfig> findByIdAndTeam(Long id, Team team);
}

