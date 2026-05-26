package com.juliashtal.devanalytics.jira.repository;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.model.UserProjectRegistration;
import com.juliashtal.devanalytics.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserProjectRegistrationRepository extends JpaRepository<UserProjectRegistration, Long> {
    boolean existsByUserAndProject(User user, JiraProjectEntity project);
    Optional<UserProjectRegistration> findByUserAndProject(User user, JiraProjectEntity project);
    List<UserProjectRegistration> findAllByUser(User user);

    /** DataSourceConfigs the user can access via Jira project subscriptions. Used by DataSourceService.listForUser. */
    @Query("SELECT DISTINCT upr.project.dataSource FROM UserProjectRegistration upr WHERE upr.user.id = :userId")
    List<DataSourceConfig> findDataSourceConfigsByUserId(@Param("userId") Long userId);

    /** True when the user has at least one Jira project subscription under the given datasource. Used by DataSourceService.getForUser. */
    @Query("SELECT COUNT(upr) > 0 FROM UserProjectRegistration upr WHERE upr.user.id = :userId AND upr.project.dataSource.id = :dataSourceId")
    boolean existsByUserIdAndDataSourceId(@Param("userId") Long userId, @Param("dataSourceId") Long dataSourceId);
}
