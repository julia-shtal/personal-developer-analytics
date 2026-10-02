package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.metrics.repository.MetricSnapshotRepository;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.Mockito.verify;

/**
 * Pins that the writer delegates straight to the database's upsert rather than reading first.
 * <p>{@link MetricSnapshotRepository#upsert} is what closes the identity race, so this only has
 * to prove the writer passes the right identity and value through.</p>
 */
@ExtendWith(MockitoExtension.class)
class MetricSnapshotWriterTest {

    @Mock MetricSnapshotRepository repository;

    @InjectMocks MetricSnapshotWriter writer;

    private static final LocalDate DATE = LocalDate.of(2024, 1, 15);

    @Test
    void save_personalDailyMetric_upsertsWithNullTeamAndRepo() {
        User user = new User();
        user.setId(1L);

        writer.save(user, null, DATE, MetricType.DAILY_COMMITS_COUNT, 7.0, null, null, null);

        verify(repository).upsert(1L, null, null, DATE, "DAILY_COMMITS_COUNT", 7.0, null, null);
    }

    @Test
    void save_teamScopedAggregateMetric_upsertsWithTeamRepoAndPeriod() {
        User user = new User();
        user.setId(1L);
        Team team = new Team();
        team.setId(5L);
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(9L);
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 7);

        writer.save(user, team, DATE, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, 12.5, repo, from, to);

        verify(repository).upsert(1L, 5L, 9L, DATE, "PR_LEAD_TIME_HOURS_MEDIAN", 12.5, from, to);
    }
}
