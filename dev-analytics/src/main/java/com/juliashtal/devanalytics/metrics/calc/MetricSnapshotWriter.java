package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.metrics.repository.MetricSnapshotRepository;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Persists a {@link MetricSnapshot} using the database's own upsert guard.
 * <p>Every {@link MetricCalculator} writes its results through this service.</p>
 */
@Service
@RequiredArgsConstructor
public class MetricSnapshotWriter {

    private final MetricSnapshotRepository repository;

    /** Upserts so two concurrent calls for the same identity overwrite rather than duplicate. */
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

        repository.upsert(user.getId(), teamId, repoId, date, metricType.name(), value, periodFrom, periodTo);
    }
}
