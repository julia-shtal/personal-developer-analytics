package com.juliashtal.devanalytics.git.repository;

import com.juliashtal.devanalytics.git.model.UserRepoRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRepoRegistrationRepository extends JpaRepository<UserRepoRegistration, Long> {

    boolean existsByUserIdAndRepositoryId(Long userId, Long repositoryId);

    java.util.Optional<UserRepoRegistration> findByUserIdAndRepositoryId(Long userId, Long repositoryId);

    @Query("SELECT urr.repository.id FROM UserRepoRegistration urr WHERE urr.user.id = :userId")
    List<Long> findRepoIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT urr.repository.id FROM UserRepoRegistration urr WHERE urr.user.id = :userId AND urr.dataSourceConfig.id = :dataSourceId")
    List<Long> findRepoIdsByUserIdAndDataSourceId(@Param("userId") Long userId, @Param("dataSourceId") Long dataSourceId);

    /** Returns distinct DataSourceConfigs the user is subscribed to (via repo registrations). */
    @Query("SELECT DISTINCT urr.dataSourceConfig FROM UserRepoRegistration urr WHERE urr.user.id = :userId AND urr.dataSourceConfig IS NOT NULL")
    List<com.juliashtal.devanalytics.datasource.model.DataSourceConfig> findDataSourceConfigsByUserId(@Param("userId") Long userId);

    boolean existsByUserIdAndDataSourceConfig_Id(Long userId, Long dataSourceConfigId);
}
