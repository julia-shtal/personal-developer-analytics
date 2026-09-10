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

    public List<MetricSnapshot> getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateBetween(User user,
                                                                                          MetricType metricType,
                                                                                          GitRepositoryEntity repo,
                                                                                          LocalDate from,
                                                                                          LocalDate to) {
        return repository.findByUserAndTeamIsNullAndMetricTypeAndRepositoryAndDateBetween(user, metricType, repo, from, to);
    }

    /**
     * Every row this window can honestly answer with: DAILY rows dated inside it plus AGGREGATE
     * rows whose window it fully contains, falling back to a covering AGGREGATE row when the
     * request is narrower than the metric's grain. Callers partition by shape via
     * {@link AggregateWindowResolver}.
     */
    public List<MetricSnapshot> getMetricSnapshotsByUserAndMetricTypeInWindow(User user,
                                                                             MetricType metricType,
                                                                             LocalDate from,
                                                                             LocalDate to) {
        List<MetricSnapshot> contained = repository.findPersonalInWindow(user, metricType, from, to);
        if (!contained.isEmpty()) return contained;
        return repository.findPersonalAggregateCovering(user, metricType, from, to);
    }

    /** Repository-scoped variant of {@link #getMetricSnapshotsByUserAndMetricTypeInWindow}. */
    public List<MetricSnapshot> getMetricSnapshotsByUserAndMetricTypeAndRepositoryInWindow(User user,
                                                                                          MetricType metricType,
                                                                                          GitRepositoryEntity repo,
                                                                                          LocalDate from,
                                                                                          LocalDate to) {
        List<MetricSnapshot> contained =
                repository.findPersonalByRepositoryInWindow(user, metricType, repo, from, to);
        if (!contained.isEmpty()) return contained;
        return repository.findPersonalAggregateCoveringByRepository(user, metricType, repo, from, to);
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

    /**
     * Team-scoped equivalent of {@link #getMetricSnapshotsByUserAndMetricTypeInWindow},
     * for the manager summary views. No covering fallback: a team summary states what is
     * stored inside the requested range rather than borrowing a wider window.
     */
    public List<MetricSnapshot> getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeInWindow(
            List<Long> userIds,
            Long teamId,
            MetricType metricType,
            LocalDate from,
            LocalDate to) {
        return repository.findByUserIdsAndTeamIdAndMetricTypeInWindow(userIds, teamId, metricType, from, to);
    }

    /** Single-member variant of {@link #getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeInWindow}. */
    public List<MetricSnapshot> getMetricSnapshotsByUserAndTeamAndMetricTypeInWindow(
            User user,
            Team team,
            MetricType metricType,
            LocalDate from,
            LocalDate to) {
        return repository.findByUserAndTeamAndMetricTypeInWindow(user, team, metricType, from, to);
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

