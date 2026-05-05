package com.juliashtal.devanalytics.ai.repository;

import com.juliashtal.devanalytics.ai.model.MetricSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricSummaryRepository extends JpaRepository<MetricSummaryEntity, Long> {
}
