package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MetricSnapshotRepository extends JpaRepository<MetricSnapshot, Long> {

    List<MetricSnapshot> findByUserAndMetricTypeAndDateBetween(
            User user,
            MetricType metricType,
            LocalDate from,
            LocalDate to
    );

    List<MetricSnapshot> findByUserAndMetricTypeAndRepositoryAndDateBetween(
            User user,
            MetricType metricType,
            GitRepositoryEntity repository,
            LocalDate from,
            LocalDate to
    );

    /**
     * Finds an existing snapshot for upsert purposes.
     * Handles nullable repository, periodFrom and periodTo correctly via IS NOT DISTINCT FROM
     * (standard SQL NULL-safe equality, supported by PostgreSQL).
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM metric_snapshots
            WHERE user_id        = :userId
              AND repository_id  IS NOT DISTINCT FROM :repoId
              AND date           = :date
              AND metric_type    = :metricType
              AND period_from    IS NOT DISTINCT FROM :periodFrom
              AND period_to      IS NOT DISTINCT FROM :periodTo
            LIMIT 1
            """)
    Optional<MetricSnapshot> findExisting(
            @Param("userId") Long userId,
            @Param("repoId") Long repoId,
            @Param("date") LocalDate date,
            @Param("metricType") String metricType,
            @Param("periodFrom") LocalDate periodFrom,
            @Param("periodTo") LocalDate periodTo
    );
}