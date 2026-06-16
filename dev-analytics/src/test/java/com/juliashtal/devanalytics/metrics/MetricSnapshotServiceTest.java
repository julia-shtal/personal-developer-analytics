package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricSnapshotServiceTest {

    @Mock MetricSnapshotRepository repository;
    @InjectMocks MetricSnapshotService service;

    private static final LocalDate FROM = LocalDate.of(2024, 1, 1);
    private static final LocalDate TO = LocalDate.of(2024, 1, 31);

    private MetricSnapshot snapshot(MetricType type, double value) {
        MetricSnapshot s = new MetricSnapshot();
        s.setMetricType(type);
        s.setValue(value);
        return s;
    }

    @Test
    void findMaxPersonalDate_delegatesToRepository() {
        LocalDate date = LocalDate.of(2026, 6, 1);
        when(repository.findMaxPersonalDate(1L)).thenReturn(Optional.of(date));

        Optional<LocalDate> result = service.findMaxPersonalDate(1L);

        assertThat(result).contains(date);
    }

    @Test
    void findMaxPersonalDate_noSnapshots_returnsEmpty() {
        when(repository.findMaxPersonalDate(1L)).thenReturn(Optional.empty());

        assertThat(service.findMaxPersonalDate(1L)).isEmpty();
    }

    @Test
    void getMetricSnapshotsByUserAndMetricTypeAndDateBetween_delegatesToRepository() {
        User user = new User();
        user.setId(1L);
        List<MetricSnapshot> expected = List.of(snapshot(MetricType.DAILY_COMMITS_COUNT, 5));
        when(repository.findByUserAndTeamIsNullAndMetricTypeAndDateBetween(user, MetricType.DAILY_COMMITS_COUNT, FROM, TO))
                .thenReturn(expected);

        List<MetricSnapshot> result = service.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(user, MetricType.DAILY_COMMITS_COUNT, FROM, TO);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo_delegatesToRepository() {
        User user = new User();
        user.setId(1L);
        List<MetricSnapshot> expected = List.of(snapshot(MetricType.PR_LEAD_TIME_HOURS_MEDIAN, 12.0));
        when(repository.findPersonalAggregateByPeriod(user, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, FROM, TO))
                .thenReturn(expected);

        List<MetricSnapshot> result = service.getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(user, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, FROM, TO);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateBetween_delegatesToRepository() {
        User user = new User();
        user.setId(1L);
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(2L);
        List<MetricSnapshot> expected = List.of(snapshot(MetricType.DAILY_COMMITS_COUNT, 3));
        when(repository.findByUserAndTeamIsNullAndMetricTypeAndRepositoryAndDateBetween(user, MetricType.DAILY_COMMITS_COUNT, repo, FROM, TO))
                .thenReturn(expected);

        List<MetricSnapshot> result = service.getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateBetween(user, MetricType.DAILY_COMMITS_COUNT, repo, FROM, TO);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateFromAndTo_delegatesToRepository() {
        User user = new User();
        user.setId(1L);
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(2L);
        List<MetricSnapshot> expected = List.of(snapshot(MetricType.REFACTOR_RATIO, 0.4));
        when(repository.findPersonalAggregateByRepositoryAndPeriod(user, MetricType.REFACTOR_RATIO, repo, FROM, TO))
                .thenReturn(expected);

        List<MetricSnapshot> result = service.getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateFromAndTo(user, MetricType.REFACTOR_RATIO, repo, FROM, TO);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween_delegatesToRepository() {
        User user = new User();
        user.setId(1L);
        Team team = new Team();
        team.setId(7L);
        List<MetricSnapshot> expected = List.of(snapshot(MetricType.DAILY_PR_CREATED, 2));
        when(repository.findByUserAndTeamAndMetricTypeAndDateBetween(user, team, MetricType.DAILY_PR_CREATED, FROM, TO))
                .thenReturn(expected);

        List<MetricSnapshot> result = service.getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(user, team, MetricType.DAILY_PR_CREATED, FROM, TO);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndDateBetween_delegatesToRepository() {
        List<Long> userIds = List.of(1L, 2L);
        List<MetricSnapshot> expected = List.of(snapshot(MetricType.DAILY_COMMITS_COUNT, 9));
        when(repository.findByUserIdsAndTeamIdAndMetricTypeAndDateBetween(userIds, 7L, MetricType.DAILY_COMMITS_COUNT, FROM, TO))
                .thenReturn(expected);

        List<MetricSnapshot> result = service.getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndDateBetween(userIds, 7L, MetricType.DAILY_COMMITS_COUNT, FROM, TO);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndRepositoryAndDateBetween_delegatesToRepository() {
        List<Long> userIds = List.of(1L, 2L);
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(3L);
        List<MetricSnapshot> expected = List.of(snapshot(MetricType.DAILY_COMMITS_COUNT, 4));
        when(repository.findByUserIdsAndTeamIdAndMetricTypeAndRepositoryAndDateBetween(userIds, 7L, MetricType.DAILY_COMMITS_COUNT, repo, FROM, TO))
                .thenReturn(expected);

        List<MetricSnapshot> result = service.getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndRepositoryAndDateBetween(userIds, 7L, MetricType.DAILY_COMMITS_COUNT, repo, FROM, TO);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getExisting_found_returnsSnapshot() {
        MetricSnapshot expected = snapshot(MetricType.DAILY_COMMITS_COUNT, 5);
        when(repository.findExisting(1L, null, null, FROM, "DAILY_COMMITS_COUNT", null, null))
                .thenReturn(Optional.of(expected));

        MetricSnapshot result = service.getExisting(1L, null, null, FROM, "DAILY_COMMITS_COUNT", null, null);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getExisting_notFound_throwsNoSuchElement() {
        when(repository.findExisting(1L, null, null, FROM, "DAILY_COMMITS_COUNT", null, null))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getExisting(1L, null, null, FROM, "DAILY_COMMITS_COUNT", null, null))
                .isInstanceOf(NoSuchElementException.class);
    }
}
