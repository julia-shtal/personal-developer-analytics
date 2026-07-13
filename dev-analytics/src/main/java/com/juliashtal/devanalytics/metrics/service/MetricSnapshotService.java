package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.metrics.MetricSnapshotRepository;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * Reads persisted metric snapshots by user, type, and date range.
 */
@Service
@RequiredArgsConstructor
public class MetricSnapshotService {

    private final MetricSnapshotRepository repository;

    public List<MetricSnapshot> getMetricSnapshotsByUserAndMetricTypeAndDateBetween(User user,
                                                                             MetricType metricType,
                                                                             LocalDate from,
                                                                             LocalDate to) {
        return repository.findByUserAndTeamIsNullAndMetricTypeAndDateBetween(user, metricType, from, to);
    }

    public List<MetricSnapshot> getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(User user,
                                                                                    MetricType metricType,
                                                                                    LocalDate from,
                                                                                    LocalDate to) {
        return repository.findPersonalAggregateByPeriod(user, metricType, from, to);
    }

    public List<MetricSnapshot> getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateBetween(User user,
                                                                                          MetricType metricType,
                                                                                          GitRepositoryEntity repo,
                                                                                          LocalDate from,
                                                                                          LocalDate to) {
        return repository.findByUserAndTeamIsNullAndMetricTypeAndRepositoryAndDateBetween(user, metricType, repo, from, to);
    }

    public List<MetricSnapshot> getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateFromAndTo(User user,
                                                                                                 MetricType metricType,
                                                                                                 GitRepositoryEntity repo,
                                                                                                 LocalDate from,
                                                                                                 LocalDate to) {
        return repository.findPersonalAggregateByRepositoryAndPeriod(user, metricType, repo, from, to);
    }

    public List<MetricSnapshot> getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(
            User user, Team team, MetricType metricType, LocalDate from, LocalDate to) {
        return repository.findByUserAndTeamAndMetricTypeAndDateBetween(user, team, metricType, from, to);
    }

    public List<MetricSnapshot> getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndDateBetween(
            List<Long> userIds,
            Long teamId,
            MetricType metricType,
            LocalDate from,
            LocalDate to) {
        return repository.findByUserIdsAndTeamIdAndMetricTypeAndDateBetween(userIds, teamId, metricType, from, to);
    }

    public List<MetricSnapshot> getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndRepositoryAndDateBetween(
            List<Long> userIds,
            Long teamId,
            MetricType metricType,
            GitRepositoryEntity repo,
            LocalDate from,
            LocalDate to) {
        return repository.findByUserIdsAndTeamIdAndMetricTypeAndRepositoryAndDateBetween(
                userIds, teamId, metricType, repo, from, to);
    }

    /** Latest personal (team IS NULL) snapshot date for a user — used for the dashboard freshness indicator. */
    public Optional<LocalDate> findMaxPersonalDate(Long userId) {
        return repository.findMaxPersonalDate(userId);
    }

    public MetricSnapshot getExisting(
            Long userId,
            Long teamId,
            Long repoId,
            LocalDate date,
            String metricType,
            LocalDate periodFrom,
            LocalDate periodTo) {
        return repository.findExisting(userId, teamId, repoId, date, metricType, periodFrom, periodTo)
                .orElseThrow(() -> new NoSuchElementException("MetricSnapshot not found for user " + userId + " and team " + teamId + " and repo " + repoId));
    }

}

