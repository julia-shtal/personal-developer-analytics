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

    // -------------------------------------------------------------------------
    // Period-overlap queries — for aggregate metrics where `date` = fromDate
    // and may predate the UI's requested range
    // -------------------------------------------------------------------------

    @Query("""
            SELECT s FROM MetricSnapshot s
            WHERE s.user = :user
              AND s.team IS NULL
              AND s.metricType = :metricType
              AND s.repository = :repository
              AND s.periodFrom IS NOT NULL
              AND s.periodFrom = :from
              AND s.periodTo = :to
            """)
    List<MetricSnapshot> findPersonalAggregateByRepositoryAndPeriod(
            @Param("user") User user,
            @Param("metricType") MetricType metricType,
            @Param("repository") GitRepositoryEntity repository,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            SELECT s FROM MetricSnapshot s
            WHERE s.user = :user
              AND s.team IS NULL
              AND s.metricType = :metricType
              AND s.periodFrom IS NOT NULL
              AND s.periodFrom = :from
              AND s.periodTo = :to
            """)
    List<MetricSnapshot> findPersonalAggregateByPeriod(
            @Param("user") User user,
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
