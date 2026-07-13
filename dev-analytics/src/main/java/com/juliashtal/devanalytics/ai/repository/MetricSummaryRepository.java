package com.juliashtal.devanalytics.ai.repository;

import com.juliashtal.devanalytics.ai.model.MetricSummaryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for MetricSummaryEntity (metric_summaries).
 * Includes the daily AI-call count and latest-summary lookups.
 */
public interface MetricSummaryRepository extends JpaRepository<MetricSummaryEntity, Long> {

    @Query(value = "SELECT COUNT(*) FROM metric_summaries WHERE DATE(generated_at) = CURRENT_DATE", nativeQuery = true)
    long countAiCallsToday();

    Optional<MetricSummaryEntity> findTopByUser_IdOrderByGeneratedAtDesc(Long userId);

    Optional<MetricSummaryEntity> findTopByTeam_IdOrderByGeneratedAtDesc(Long teamId);

    List<MetricSummaryEntity> findByUser_IdOrderByGeneratedAtDesc(Long userId, Pageable pageable);

    List<MetricSummaryEntity> findByTeam_IdOrderByGeneratedAtDesc(Long teamId, Pageable pageable);

    /** Null-safe identity lookup mirroring the uix_metric_summaries_identity index. */
    @Query(value = """
            SELECT * FROM metric_summaries
            WHERE user_id IS NOT DISTINCT FROM :userId
              AND team_id IS NOT DISTINCT FROM :teamId
              AND period_from = :from
              AND period_to = :to
              AND scope = :scope
              AND context_repo_name IS NOT DISTINCT FROM :contextName
            LIMIT 1
            """, nativeQuery = true)
    Optional<MetricSummaryEntity> findByIdentity(
            @Param("userId") Long userId,
            @Param("teamId") Long teamId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("scope") String scope,
            @Param("contextName") String contextName
    );
}
