package com.juliashtal.devanalytics.ai.repository;

import com.juliashtal.devanalytics.ai.model.MetricSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MetricSummaryRepository extends JpaRepository<MetricSummaryEntity, Long> {

    @Query(value = "SELECT COUNT(*) FROM metric_summaries WHERE DATE(generated_at) = CURRENT_DATE", nativeQuery = true)
    long countAiCallsToday();
}
