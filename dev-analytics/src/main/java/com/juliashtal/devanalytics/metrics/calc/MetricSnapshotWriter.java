package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.metrics.MetricSnapshotRepository;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Persists a {@link MetricSnapshot} using the native-SQL upsert guard.
 * Extracted from {@code MetricsService.saveMetric} — logic is unchanged.
 * All {@link MetricCalculator} beans call this service to write their results.
 */
@Service
@RequiredArgsConstructor
public class MetricSnapshotWriter {

    private final MetricSnapshotRepository repository;

    public void save(User user,
                     Team team,
                     LocalDate date,
                     MetricType metricType,
                     double value,
                     GitRepositoryEntity repo,
                     LocalDate periodFrom,
                     LocalDate periodTo) {
        Long teamId = team != null ? team.getId() : null;
        Long repoId = repo  != null ? repo.getId()  : null;

        MetricSnapshot snapshot = repository
                .findExisting(user.getId(), teamId, repoId, date, metricType.name(), periodFrom, periodTo)
                .orElseGet(MetricSnapshot::new);

        snapshot.setUser(user);
        snapshot.setTeam(team);
        snapshot.setRepository(repo);
        snapshot.setDate(date);
        snapshot.setMetricType(metricType);
        snapshot.setPeriodFrom(periodFrom);
        snapshot.setPeriodTo(periodTo);
        snapshot.setValue(value);

        repository.save(snapshot);
    }
}
