package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.metrics.model.MetricCoverage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Spring Data repository for MetricCoverage (metric_coverage) — the per-user record of which
 * calendar days personal metrics have been computed for.
 */
public interface MetricCoverageRepository extends JpaRepository<MetricCoverage, Long> {

    @Query("""
            SELECT c.date FROM MetricCoverage c
            WHERE c.user.id = :userId
              AND c.date BETWEEN :from AND :to
            ORDER BY c.date
            """)
    List<LocalDate> findDatesInRange(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Native because JPQL cannot express {@code ON CONFLICT}. Marking a day covered must be
     * idempotent: both the backfill and the nightly job re-mark the same day.
     * {@code computed_at} is refreshed, so the row also answers when it was last recomputed.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO metric_coverage (user_id, date, computed_at)
            VALUES (:userId, :date, now())
            ON CONFLICT (user_id, date) DO UPDATE SET computed_at = now()
            """)
    void markCovered(@Param("userId") Long userId, @Param("date") LocalDate date);

    /**
     * Carries its own transaction because its callers do not supply one: the backfill resets a
     * user's coverage outside any enclosing transaction so the recomputation that follows can
     * commit block by block, and a modifying query without a read-write transaction is rejected.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM MetricCoverage c WHERE c.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
