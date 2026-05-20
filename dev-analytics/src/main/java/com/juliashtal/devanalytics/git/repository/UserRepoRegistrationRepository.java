package com.juliashtal.devanalytics.git.repository;

import com.juliashtal.devanalytics.git.model.UserRepoRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRepoRegistrationRepository extends JpaRepository<UserRepoRegistration, Long> {

    boolean existsByUserIdAndRepositoryId(Long userId, Long repositoryId);

    java.util.Optional<UserRepoRegistration> findByUserIdAndRepositoryId(Long userId, Long repositoryId);

    /** Number of subscribers to a repo other than the given user. Used to block detach when subscriptions exist. */
    @Query("SELECT COUNT(urr) FROM UserRepoRegistration urr WHERE urr.repository.id = :repoId AND urr.user.id <> :excludeUserId")
    long countSubscribersExcludingUser(@Param("repoId") Long repoId, @Param("excludeUserId") Long excludeUserId);

    @Query("SELECT urr.repository.id FROM UserRepoRegistration urr WHERE urr.user.id = :userId")
    List<Long> findRepoIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT urr.repository.id FROM UserRepoRegistration urr WHERE urr.user.id = :userId AND urr.repository.dataSourceConfig.id = :dataSourceId")
    List<Long> findRepoIdsByUserIdAndDataSourceId(@Param("userId") Long userId, @Param("dataSourceId") Long dataSourceId);

    /** Returns distinct DataSourceConfigs the user is subscribed to, resolved via the repo's datasource. */
    @Query("SELECT DISTINCT urr.repository.dataSourceConfig FROM UserRepoRegistration urr WHERE urr.user.id = :userId")
    List<com.juliashtal.devanalytics.datasource.model.DataSourceConfig> findDataSourceConfigsByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(urr) > 0 FROM UserRepoRegistration urr WHERE urr.user.id = :userId AND urr.repository.dataSourceConfig.id = :dataSourceConfigId")
    boolean existsByUserIdAndDataSourceConfig_Id(@Param("userId") Long userId, @Param("dataSourceConfigId") Long dataSourceConfigId);
}
