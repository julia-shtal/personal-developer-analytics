package com.juliashtal.devanalytics.git.repository;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for GitRepositoryEntity (git_repositories). Canonical-row lookups by full name.
 */
public interface GitRepositoryEntityRepository extends JpaRepository<GitRepositoryEntity, Long> {
    List<GitRepositoryEntity> findAllByDataSourceConfig(DataSourceConfig dataSourceConfig);
    long countByDataSourceConfig(DataSourceConfig dataSourceConfig);
    Optional<GitRepositoryEntity> findByDataSourceConfigAndName(
            DataSourceConfig cfg, String name
    );
    Optional<GitRepositoryEntity> findByRepoFullName(String repoFullName);

    @Query("SELECT r.id FROM GitRepositoryEntity r WHERE r.dataSourceConfig.team.id IN :teamIds")
    List<Long> findIdsByTeamIds(@Param("teamIds") List<Long> teamIds);

    @Query("SELECT r FROM GitRepositoryEntity r JOIN FETCH r.dataSourceConfig WHERE r.id = :id")
    Optional<GitRepositoryEntity> findByIdWithDataSourceConfig(@Param("id") Long id);

    /**
     * Loads repos by IDs with their data source and team eagerly fetched.
     * LEFT JOIN FETCH on team avoids LazyInitializationException when building RepoDtos
     * that expose the team FK for UI grouping.
     */
    @Query("SELECT r FROM GitRepositoryEntity r JOIN FETCH r.dataSourceConfig ds LEFT JOIN FETCH ds.team WHERE r.id IN :ids")
    List<GitRepositoryEntity> findAllByIdWithDataSourceConfig(@Param("ids") Collection<Long> ids);

    // ── user_accessible_repos view queries ─────────────────────────────

    /** All repo IDs the user can access across owned, subscribed, and team paths. */
    @Query(value = "SELECT DISTINCT repo_id FROM user_accessible_repos WHERE user_id = :userId",
           nativeQuery = true)
    List<Long> findAccessibleRepoIds(@Param("userId") Long userId);

    /** Repo IDs accessible to the user that belong to a specific datasource. */
    @Query(value = """
           SELECT DISTINCT repo_id FROM user_accessible_repos
           WHERE user_id = :userId AND data_source_id = :dataSourceId
           """, nativeQuery = true)
    List<Long> findAccessibleRepoIdsByDataSource(@Param("userId") Long userId,
                                                 @Param("dataSourceId") Long dataSourceId);

    /**
     * Whether the user can reach one specific repo, over the same owned, subscribed and team
     * paths the view already defines. Native because the view is not a JPA entity; EXISTS so a
     * single-repo check does not load every accessible id just to test one membership.
     */
    @Query(value = """
           SELECT EXISTS (
               SELECT 1 FROM user_accessible_repos
               WHERE user_id = :userId AND repo_id = :repoId
           )
           """, nativeQuery = true)
    boolean existsAccessibleRepo(@Param("userId") Long userId, @Param("repoId") Long repoId);

    /**
     * Whether one repo belongs to a team's own data sources. Team scope is a plain FK walk
     * rather than a view branch, so this stays JPQL — the view answers what a <em>user</em>
     * can reach, which is a different question from what a <em>team</em> owns.
     */
    @Query("""
           SELECT COUNT(r) > 0 FROM GitRepositoryEntity r
           WHERE r.id = :repoId AND r.dataSourceConfig.team.id = :teamId
           """)
    boolean existsByIdAndTeamId(@Param("repoId") Long repoId, @Param("teamId") Long teamId);
}

