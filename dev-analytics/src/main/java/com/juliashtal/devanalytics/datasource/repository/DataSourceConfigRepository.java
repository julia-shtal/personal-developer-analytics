package com.juliashtal.devanalytics.datasource.repository;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for DataSourceConfig (data_source_configs). Scopes datasources per user.
 */
public interface DataSourceConfigRepository extends JpaRepository<DataSourceConfig, Long> {
    List<DataSourceConfig> findAllByUser(User user);
    List<DataSourceConfig> findAllByUserAndTeamIsNull(User user);

    /** Explicit user-ID variant — avoids Hibernate proxy resolution issues. */
    @Query("SELECT ds FROM DataSourceConfig ds WHERE ds.user.id = :userId")
    List<DataSourceConfig> findAllByUserId(@Param("userId") Long userId);
    Optional<DataSourceConfig> findByIdAndUser(Long id, User user);

    List<DataSourceConfig> findAllByTeam(Team team);
    Optional<DataSourceConfig> findByIdAndTeam(Long id, Team team);
}

