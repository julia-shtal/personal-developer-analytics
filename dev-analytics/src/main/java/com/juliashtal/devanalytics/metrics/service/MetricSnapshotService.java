package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.metrics.MetricSnapshotRepository;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MetricSnapshotService {

    private final MetricSnapshotRepository repository;

    List<MetricSnapshot> getMetricSnapshotsByUserAndMetricTypeAndDateBetween(User user,
                                                                             MetricType metricType,
                                                                             LocalDate from,
                                                                             LocalDate to) {
        return repository.findByUserAndMetricTypeAndDateBetween(user, metricType, from, to);
    }

    List<MetricSnapshot> getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateBetween(User user,
                                                                                          MetricType metricType,
                                                                                          GitRepositoryEntity repo,
                                                                                          LocalDate from,
                                                                                          LocalDate to) {
        return repository.findByUserAndMetricTypeAndRepositoryAndDateBetween(user, metricType, repo, from, to);
    }

}

