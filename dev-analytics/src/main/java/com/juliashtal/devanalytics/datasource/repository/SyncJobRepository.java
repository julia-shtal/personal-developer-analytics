package com.juliashtal.devanalytics.datasource.repository;

import com.juliashtal.devanalytics.datasource.model.SyncJobEntity;
import com.juliashtal.devanalytics.datasource.model.SyncJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface SyncJobRepository extends JpaRepository<SyncJobEntity, Long> {

    /** Returns the most recent job record for a given data source, regardless of status. */
    Optional<SyncJobEntity> findTopByDataSourceIdOrderByStartedAtDesc(Long dataSourceId);

    /** Called at startup: marks any job still in RUNNING state as INTERRUPTED. */
    @Modifying
    @Transactional
    @Query("UPDATE SyncJobEntity s SET s.status = :interrupted, s.completedAt = :now WHERE s.status = :running")
    int markInterrupted(@Param("running") SyncJobStatus running,
                        @Param("interrupted") SyncJobStatus interrupted,
                        @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("UPDATE SyncJobEntity s SET s.status = :status, s.completedAt = :now, s.result = :result, s.totalProcessed = :total WHERE s.id = :id")
    void markCompleted(@Param("id") Long id,
                       @Param("status") SyncJobStatus status,
                       @Param("now") Instant now,
                       @Param("result") String result,
                       @Param("total") Integer total);

    @Modifying
    @Transactional
    @Query("UPDATE SyncJobEntity s SET s.status = :status, s.completedAt = :now, s.error = :error WHERE s.id = :id")
    void markFailed(@Param("id") Long id,
                    @Param("status") SyncJobStatus status,
                    @Param("now") Instant now,
                    @Param("error") String error);

    @Modifying
    @Transactional
    @Query("UPDATE SyncJobEntity s SET s.phase = :phase WHERE s.id = :id")
    void updatePhase(@Param("id") Long id, @Param("phase") String phase);
}
