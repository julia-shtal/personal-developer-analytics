package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for MetricSnapshot (metric_snapshots). Personal and team metric queries plus the upsert guard.
 */
public interface MetricSnapshotRepository extends JpaRepository<MetricSnapshot, Long> {

    // -------------------------------------------------------------------------
    // Personal metrics (team IS NULL)
    // -------------------------------------------------------------------------

    List<MetricSnapshot> findByUserAndTeamIsNullAndMetricTypeAndDateBetween(
            User user, MetricType metricType, LocalDate from, LocalDate to);

    List<MetricSnapshot> findByUserAndTeamIsNullAndMetricTypeAndRepositoryAndDateBetween(
            User user, MetricType metricType, GitRepositoryEntity repository,
            LocalDate from, LocalDate to);

    // -------------------------------------------------------------------------
    // Team-scoped metrics (team = X)
    // -------------------------------------------------------------------------

    List<MetricSnapshot> findByUserAndTeamAndMetricTypeAndDateBetween(
            User user, Team team, MetricType metricType, LocalDate from, LocalDate to);

    List<MetricSnapshot> findByUserAndTeamAndMetricTypeAndRepositoryAndDateBetween(
            User user, Team team, MetricType metricType, GitRepositoryEntity repository,
            LocalDate from, LocalDate to);

    // -------------------------------------------------------------------------
    // Team aggregate: all members of a team, for manager view
    // -------------------------------------------------------------------------

    /**
     * Personal snapshots (team IS NULL) for a list of users — used by the
     * manager team-view so it shows real member data regardless of whether
     * a separate team-scoped calculation was ever triggered.
     */
    @Query("""
            SELECT s FROM MetricSnapshot s
            WHERE s.user.id IN :userIds
              AND s.team IS NULL
              AND s.metricType = :metricType
              AND s.date BETWEEN :from AND :to
            """)
    List<MetricSnapshot> findPersonalByUserIdsAndMetricTypeAndDateBetween(
            @Param("userIds") List<Long> userIds,
            @Param("metricType") MetricType metricType,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Legacy: team-scoped snapshots — kept for future team-datasource use. */
    @Query("""
            SELECT s FROM MetricSnapshot s
            WHERE s.user.id IN :userIds
              AND s.team.id = :teamId
              AND s.metricType = :metricType
              AND s.date BETWEEN :from AND :to
            """)
    List<MetricSnapshot> findByUserIdsAndTeamIdAndMetricTypeAndDateBetween(
            @Param("userIds") List<Long> userIds,
            @Param("teamId") Long teamId,
            @Param("metricType") MetricType metricType,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Team-scoped snapshots filtered to a specific repository. Used when team dashboard has a repo filter active. */
    @Query("""
            SELECT s FROM MetricSnapshot s
            WHERE s.user.id IN :userIds
              AND s.team.id = :teamId
              AND s.metricType = :metricType
              AND s.repository = :repository
              AND s.date BETWEEN :from AND :to
            """)
    List<MetricSnapshot> findByUserIdsAndTeamIdAndMetricTypeAndRepositoryAndDateBetween(
            @Param("userIds") List<Long> userIds,
            @Param("teamId") Long teamId,
            @Param("metricType") MetricType metricType,
            @Param("repository") GitRepositoryEntity repository,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // -------------------------------------------------------------------------
    // Window-resolution queries — route by the STORED ROW SHAPE, never by a list
    // of metric types. A row is DAILY when period_from IS NULL and AGGREGATE when
    // it is set (see MetricSnapshot's class javadoc). Keeping the shape test in
    // the query is what stops the read path from having to know which types are
    // period-stored — the disagreement that TASK 01 was raised for.
    // -------------------------------------------------------------------------

    /**
     * Every row for this metric that the requested window can honestly answer with:
     * DAILY rows whose {@code date} falls in the window, plus AGGREGATE rows whose
     * {@code [periodFrom, periodTo]} is fully contained by it. Callers partition the
     * result by shape and reduce each part appropriately.
     */
    @Query("""
            SELECT s FROM MetricSnapshot s
            WHERE s.user = :user
              AND s.team IS NULL
              AND s.metricType = :metricType
              AND ((s.periodFrom IS NULL AND s.date BETWEEN :from AND :to)
                OR (s.periodFrom IS NOT NULL AND s.periodFrom >= :from AND s.periodTo <= :to))
            """)
    List<MetricSnapshot> findPersonalInWindow(
            @Param("user") User user,
            @Param("metricType") MetricType metricType,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Repository-scoped variant of {@link #findPersonalInWindow}. */
    @Query("""
            SELECT s FROM MetricSnapshot s
            WHERE s.user = :user
              AND s.team IS NULL
              AND s.metricType = :metricType
              AND s.repository = :repository
              AND ((s.periodFrom IS NULL AND s.date BETWEEN :from AND :to)
                OR (s.periodFrom IS NOT NULL AND s.periodFrom >= :from AND s.periodTo <= :to))
            """)
    List<MetricSnapshot> findPersonalByRepositoryInWindow(
            @Param("user") User user,
            @Param("metricType") MetricType metricType,
            @Param("repository") GitRepositoryEntity repository,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * AGGREGATE rows whose window fully covers the request. Fallback for requests
     * narrower than the grain the metric was computed on — a three-day request
     * against ISO-week rows contains nothing but is contained by one week. The
     * caller reports the covering window, so the figure is never labelled with a
     * window it was not computed over.
     */
    @Query("""
            SELECT s FROM MetricSnapshot s
            WHERE s.user = :user
              AND s.team IS NULL
              AND s.metricType = :metricType
              AND s.periodFrom IS NOT NULL
              AND s.periodFrom <= :from
              AND s.periodTo >= :to
            """)
    List<MetricSnapshot> findPersonalAggregateCovering(
            @Param("user") User user,
            @Param("metricType") MetricType metricType,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Repository-scoped variant of {@link #findPersonalAggregateCovering}. */
    @Query("""
            SELECT s FROM MetricSnapshot s
            WHERE s.user = :user
              AND s.team IS NULL
              AND s.metricType = :metricType
              AND s.repository = :repository
              AND s.periodFrom IS NOT NULL
              AND s.periodFrom <= :from
              AND s.periodTo >= :to
            """)
    List<MetricSnapshot> findPersonalAggregateCoveringByRepository(
            @Param("user") User user,
            @Param("metricType") MetricType metricType,
            @Param("repository") GitRepositoryEntity repository,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Team-scoped equivalent of {@link #findPersonalInWindow} for the manager
     * team summary. Replaces the plain date-between query that summed AGGREGATE
     * rows across overlapping windows.
     */
    @Query("""
            SELECT s FROM MetricSnapshot s
            WHERE s.user.id IN :userIds
              AND s.team.id = :teamId
              AND s.metricType = :metricType
              AND ((s.periodFrom IS NULL AND s.date BETWEEN :from AND :to)
                OR (s.periodFrom IS NOT NULL AND s.periodFrom >= :from AND s.periodTo <= :to))
            """)
    List<MetricSnapshot> findByUserIdsAndTeamIdAndMetricTypeInWindow(
            @Param("userIds") List<Long> userIds,
            @Param("teamId") Long teamId,
            @Param("metricType") MetricType metricType,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Single-member variant of {@link #findByUserIdsAndTeamIdAndMetricTypeInWindow}. */
    @Query("""
            SELECT s FROM MetricSnapshot s
            WHERE s.user = :user
              AND s.team = :team
              AND s.metricType = :metricType
              AND ((s.periodFrom IS NULL AND s.date BETWEEN :from AND :to)
                OR (s.periodFrom IS NOT NULL AND s.periodFrom >= :from AND s.periodTo <= :to))
            """)
    List<MetricSnapshot> findByUserAndTeamAndMetricTypeInWindow(
            @Param("user") User user,
            @Param("team") Team team,
            @Param("metricType") MetricType metricType,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // -------------------------------------------------------------------------
    // Freshness / backfill helpers
    // -------------------------------------------------------------------------

    /**
     * Latest personal (team IS NULL) snapshot date for a user.
     * Used by the nightly scheduler for gap detection.
     */
    @Query("SELECT MAX(s.date) FROM MetricSnapshot s WHERE s.user.id = :userId AND s.team IS NULL")
    Optional<LocalDate> findMaxPersonalDate(@Param("userId") Long userId);

    // -------------------------------------------------------------------------
    // Upsert guard — includes team_id so personal and team snapshots never clash
    // -------------------------------------------------------------------------

    long countByUserId(Long userId);

    @Query(nativeQuery = true, value = """
            SELECT * FROM metric_snapshots
            WHERE user_id       = :userId
              AND team_id        IS NOT DISTINCT FROM :teamId
              AND repository_id  IS NOT DISTINCT FROM :repoId
              AND date           = :date
              AND metric_type    = :metricType
              AND period_from    IS NOT DISTINCT FROM :periodFrom
              AND period_to      IS NOT DISTINCT FROM :periodTo
            LIMIT 1
            """)
    Optional<MetricSnapshot> findExisting(
            @Param("userId") Long userId,
            @Param("teamId") Long teamId,
            @Param("repoId") Long repoId,
            @Param("date") LocalDate date,
            @Param("metricType") String metricType,
            @Param("periodFrom") LocalDate periodFrom,
            @Param("periodTo") LocalDate periodTo);
}
