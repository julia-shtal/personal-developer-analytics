package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

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
}

