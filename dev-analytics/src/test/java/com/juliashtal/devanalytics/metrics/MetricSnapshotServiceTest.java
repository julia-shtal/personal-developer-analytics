package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.repository.MetricSnapshotRepository;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.metrics.service.RetentionPolicy;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricSnapshotServiceTest {

    @Mock
    MetricSnapshotRepository repository;
    @Mock RetentionPolicy retentionPolicy;
    @InjectMocks MetricSnapshotService service;

    private static final LocalDate FROM = LocalDate.of(2024, 1, 1);
    private static final LocalDate TO = LocalDate.of(2024, 1, 31);

    @BeforeEach
    void setUp() {
        // LocalDate.MIN never clamps FROM, so every existing delegation test keeps its meaning.
        lenient().when(retentionPolicy.horizon()).thenReturn(LocalDate.MIN);
    }

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
    void findDateOfLatestCalculation_noRepository_returnsTheFirstRowOfTheOrderedQuery() {
        LocalDate latestRun = LocalDate.of(2025, 9, 1);
        when(repository.findDatesByLatestCalculation(1L, MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN))
                .thenReturn(List.of(latestRun, LocalDate.of(2026, 9, 14)));

        assertThat(service.findDateOfLatestCalculation(
                1L, MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN, null)).contains(latestRun);
    }

    @Test
    void findDateOfLatestCalculation_laterWindowCalculatedEarlier_isNotChosen() {
        // A later date whose calculation ran earlier must not win.
        when(repository.findDatesByLatestCalculation(1L, MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN))
                .thenReturn(List.of(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 9, 14)));

        assertThat(service.findDateOfLatestCalculation(
                1L, MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN, null))
                .contains(LocalDate.of(2026, 6, 1));
    }

    @Test
    void findDateOfLatestCalculation_withRepository_asksTheScopedQuery() {
        GitRepositoryEntity repo = new GitRepositoryEntity();
        LocalDate date = LocalDate.of(2026, 6, 1);
        when(repository.findDatesByLatestCalculationForRepository(
                1L, MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN, repo)).thenReturn(List.of(date));

        assertThat(service.findDateOfLatestCalculation(
                1L, MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN, repo)).contains(date);
        verify(repository, never()).findDatesByLatestCalculation(any(), any());
    }

    @Test
    void findDateOfLatestCalculation_neverCalculated_returnsEmpty() {
        when(repository.findDatesByLatestCalculation(1L, MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN))
                .thenReturn(List.of());

        assertThat(service.findDateOfLatestCalculation(
                1L, MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN, null)).isEmpty();
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
    void getMetricSnapshotsByUserAndMetricTypeInWindow_delegatesToRepository() {
        User user = new User();
        user.setId(1L);
        List<MetricSnapshot> expected = List.of(snapshot(MetricType.PR_LEAD_TIME_HOURS_MEDIAN, 12.0));
        when(repository.findPersonalInWindow(user, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, FROM, TO))
                .thenReturn(expected);

        List<MetricSnapshot> result = service.getMetricSnapshotsByUserAndMetricTypeInWindow(user, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, FROM, TO);

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
    void getMetricSnapshotsByUserAndMetricTypeAndRepositoryInWindow_delegatesToRepository() {
        User user = new User();
        user.setId(1L);
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(2L);
        List<MetricSnapshot> expected = List.of(snapshot(MetricType.REFACTOR_RATIO, 0.4));
        when(repository.findPersonalByRepositoryInWindow(user, MetricType.REFACTOR_RATIO, repo, FROM, TO))
                .thenReturn(expected);

        List<MetricSnapshot> result = service.getMetricSnapshotsByUserAndMetricTypeAndRepositoryInWindow(user, MetricType.REFACTOR_RATIO, repo, FROM, TO);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getMetricSnapshotsByUserAndMetricTypeInWindow_nothingContained_fallsBackToCoveringWindow() {
        // A request narrower than the grain the metric was computed on contains no stored
        // window. Rather than report no data, resolve to the window that covers the request;
        // the caller labels the figure with that wider window.
        User user = new User();
        user.setId(1L);
        List<MetricSnapshot> covering = List.of(snapshot(MetricType.PR_LEAD_TIME_HOURS_MEDIAN, 30.0));
        when(repository.findPersonalInWindow(user, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, FROM, TO))
                .thenReturn(List.of());
        when(repository.findPersonalAggregateCovering(user, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, FROM, TO))
                .thenReturn(covering);

        List<MetricSnapshot> result =
                service.getMetricSnapshotsByUserAndMetricTypeInWindow(user, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, FROM, TO);

        assertThat(result).isEqualTo(covering);
    }

    @Test
    void getMetricSnapshotsByUserAndMetricTypeInWindow_somethingContained_doesNotQueryCovering() {
        User user = new User();
        user.setId(1L);
        when(repository.findPersonalInWindow(user, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, FROM, TO))
                .thenReturn(List.of(snapshot(MetricType.PR_LEAD_TIME_HOURS_MEDIAN, 12.0)));

        service.getMetricSnapshotsByUserAndMetricTypeInWindow(user, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, FROM, TO);

        verify(repository, never()).findPersonalAggregateCovering(any(), any(), any(), any());
    }

    @Test
    void getMetricSnapshotsByUserAndMetricTypeAndRepositoryInWindow_nothingContained_fallsBackToCoveringWindow() {
        User user = new User();
        user.setId(1L);
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(2L);
        List<MetricSnapshot> covering = List.of(snapshot(MetricType.REFACTOR_RATIO, 0.6));
        when(repository.findPersonalByRepositoryInWindow(user, MetricType.REFACTOR_RATIO, repo, FROM, TO))
                .thenReturn(List.of());
        when(repository.findPersonalAggregateCoveringByRepository(user, MetricType.REFACTOR_RATIO, repo, FROM, TO))
                .thenReturn(covering);

        List<MetricSnapshot> result = service
                .getMetricSnapshotsByUserAndMetricTypeAndRepositoryInWindow(user, MetricType.REFACTOR_RATIO, repo, FROM, TO);

        assertThat(result).isEqualTo(covering);
    }

    @Test
    void getMetricSnapshotsByUserAndMetricTypeInWindow_windowEndsBeforeHorizon_returnsEmptyWithoutQuerying() {
        User user = new User();
        user.setId(1L);
        when(retentionPolicy.horizon()).thenReturn(LocalDate.of(2024, 2, 15));

        List<MetricSnapshot> result =
                service.getMetricSnapshotsByUserAndMetricTypeInWindow(user, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, FROM, TO);

        assertThat(result).isEmpty();
        verify(repository, never()).findPersonalInWindow(any(), any(), any(), any());
        verify(repository, never()).findPersonalAggregateCovering(any(), any(), any(), any());
    }

    @Test
    void getMetricSnapshotsByUserAndMetricTypeAndRepositoryInWindow_windowEndsBeforeHorizon_returnsEmptyWithoutQuerying() {
        User user = new User();
        user.setId(1L);
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(2L);
        when(retentionPolicy.horizon()).thenReturn(LocalDate.of(2024, 2, 15));

        List<MetricSnapshot> result = service
                .getMetricSnapshotsByUserAndMetricTypeAndRepositoryInWindow(user, MetricType.REFACTOR_RATIO, repo, FROM, TO);

        assertThat(result).isEmpty();
        verify(repository, never()).findPersonalByRepositoryInWindow(any(), any(), any(), any(), any());
        verify(repository, never()).findPersonalAggregateCoveringByRepository(any(), any(), any(), any(), any());
    }

    @Test
    void getMetricSnapshotsByUserAndMetricTypeInWindow_windowEndsOnHorizon_stillQueriesFromHorizon() {
        User user = new User();
        user.setId(1L);
        LocalDate horizon = LocalDate.of(2024, 1, 31);
        List<MetricSnapshot> covering = List.of(snapshot(MetricType.PR_LEAD_TIME_HOURS_MEDIAN, 30.0));
        when(retentionPolicy.horizon()).thenReturn(horizon);
        when(repository.findPersonalInWindow(user, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, horizon, TO))
                .thenReturn(List.of());
        when(repository.findPersonalAggregateCovering(user, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, horizon, TO))
                .thenReturn(covering);

        List<MetricSnapshot> result =
                service.getMetricSnapshotsByUserAndMetricTypeInWindow(user, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, FROM, TO);

        assertThat(result).isEqualTo(covering);
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

    @Test
    void getMetricSnapshotsByUserAndMetricTypeAndDateBetween_fromBeforeHorizon_isClampedToHorizon() {
        User user = new User();
        user.setId(1L);
        LocalDate horizon = LocalDate.of(2024, 1, 15);
        when(retentionPolicy.horizon()).thenReturn(horizon);
        when(repository.findByUserAndTeamIsNullAndMetricTypeAndDateBetween(user, MetricType.DAILY_COMMITS_COUNT, horizon, TO))
                .thenReturn(List.of());

        service.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(user, MetricType.DAILY_COMMITS_COUNT, FROM, TO);

        verify(repository).findByUserAndTeamIsNullAndMetricTypeAndDateBetween(user, MetricType.DAILY_COMMITS_COUNT, horizon, TO);
    }

    @Test
    void getMetricSnapshotsByUserAndMetricTypeAndDateBetween_fromAfterHorizon_isUnchanged() {
        User user = new User();
        user.setId(1L);
        when(retentionPolicy.horizon()).thenReturn(LocalDate.of(2020, 1, 1));
        when(repository.findByUserAndTeamIsNullAndMetricTypeAndDateBetween(user, MetricType.DAILY_COMMITS_COUNT, FROM, TO))
                .thenReturn(List.of());

        service.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(user, MetricType.DAILY_COMMITS_COUNT, FROM, TO);

        verify(repository).findByUserAndTeamIsNullAndMetricTypeAndDateBetween(user, MetricType.DAILY_COMMITS_COUNT, FROM, TO);
    }

    @Test
    void getMetricSnapshotsByUserAndMetricTypeInWindow_fromBeforeHorizon_isClampedToHorizon() {
        User user = new User();
        user.setId(1L);
        LocalDate horizon = LocalDate.of(2024, 1, 15);
        when(retentionPolicy.horizon()).thenReturn(horizon);
        when(repository.findPersonalInWindow(user, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, horizon, TO))
                .thenReturn(List.of());

        service.getMetricSnapshotsByUserAndMetricTypeInWindow(user, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, FROM, TO);

        verify(repository).findPersonalInWindow(user, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, horizon, TO);
    }

    @Test
    void getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeInWindow_fromBeforeHorizon_isClampedToHorizon() {
        List<Long> userIds = List.of(1L, 2L);
        LocalDate horizon = LocalDate.of(2024, 1, 15);
        when(retentionPolicy.horizon()).thenReturn(horizon);
        when(repository.findByUserIdsAndTeamIdAndMetricTypeInWindow(userIds, 7L, MetricType.DAILY_COMMITS_COUNT, horizon, TO))
                .thenReturn(List.of());

        service.getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeInWindow(userIds, 7L, MetricType.DAILY_COMMITS_COUNT, FROM, TO);

        verify(repository).findByUserIdsAndTeamIdAndMetricTypeInWindow(userIds, 7L, MetricType.DAILY_COMMITS_COUNT, horizon, TO);
    }

    @Test
    void getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateBetween_fromBeforeHorizon_isClampedToHorizon() {
        User user = new User();
        user.setId(1L);
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(2L);
        LocalDate horizon = LocalDate.of(2024, 1, 15);
        when(retentionPolicy.horizon()).thenReturn(horizon);
        when(repository.findByUserAndTeamIsNullAndMetricTypeAndRepositoryAndDateBetween(user, MetricType.DAILY_COMMITS_COUNT, repo, horizon, TO))
                .thenReturn(List.of());

        service.getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateBetween(user, MetricType.DAILY_COMMITS_COUNT, repo, FROM, TO);

        verify(repository).findByUserAndTeamIsNullAndMetricTypeAndRepositoryAndDateBetween(user, MetricType.DAILY_COMMITS_COUNT, repo, horizon, TO);
    }

    @Test
    void getMetricSnapshotsByUserAndMetricTypeAndRepositoryInWindow_fromBeforeHorizon_isClampedToHorizon() {
        User user = new User();
        user.setId(1L);
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(2L);
        LocalDate horizon = LocalDate.of(2024, 1, 15);
        when(retentionPolicy.horizon()).thenReturn(horizon);
        when(repository.findPersonalByRepositoryInWindow(user, MetricType.REFACTOR_RATIO, repo, horizon, TO))
                .thenReturn(List.of());

        service.getMetricSnapshotsByUserAndMetricTypeAndRepositoryInWindow(user, MetricType.REFACTOR_RATIO, repo, FROM, TO);

        verify(repository).findPersonalByRepositoryInWindow(user, MetricType.REFACTOR_RATIO, repo, horizon, TO);
    }

    @Test
    void getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween_fromBeforeHorizon_isClampedToHorizon() {
        User user = new User();
        user.setId(1L);
        Team team = new Team();
        team.setId(7L);
        LocalDate horizon = LocalDate.of(2024, 1, 15);
        when(retentionPolicy.horizon()).thenReturn(horizon);
        when(repository.findByUserAndTeamAndMetricTypeAndDateBetween(user, team, MetricType.DAILY_PR_CREATED, horizon, TO))
                .thenReturn(List.of());

        service.getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(user, team, MetricType.DAILY_PR_CREATED, FROM, TO);

        verify(repository).findByUserAndTeamAndMetricTypeAndDateBetween(user, team, MetricType.DAILY_PR_CREATED, horizon, TO);
    }

    @Test
    void getMetricSnapshotsByUserAndTeamAndMetricTypeInWindow_fromBeforeHorizon_isClampedToHorizon() {
        User user = new User();
        user.setId(1L);
        Team team = new Team();
        team.setId(7L);
        LocalDate horizon = LocalDate.of(2024, 1, 15);
        when(retentionPolicy.horizon()).thenReturn(horizon);
        when(repository.findByUserAndTeamAndMetricTypeInWindow(user, team, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, horizon, TO))
                .thenReturn(List.of());

        service.getMetricSnapshotsByUserAndTeamAndMetricTypeInWindow(user, team, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, FROM, TO);

        verify(repository).findByUserAndTeamAndMetricTypeInWindow(user, team, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, horizon, TO);
    }

    @Test
    void getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndRepositoryAndDateBetween_fromBeforeHorizon_isClampedToHorizon() {
        List<Long> userIds = List.of(1L, 2L);
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(3L);
        LocalDate horizon = LocalDate.of(2024, 1, 15);
        when(retentionPolicy.horizon()).thenReturn(horizon);
        when(repository.findByUserIdsAndTeamIdAndMetricTypeAndRepositoryAndDateBetween(userIds, 7L, MetricType.DAILY_COMMITS_COUNT, repo, horizon, TO))
                .thenReturn(List.of());

        service.getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndRepositoryAndDateBetween(userIds, 7L, MetricType.DAILY_COMMITS_COUNT, repo, FROM, TO);

        verify(repository).findByUserIdsAndTeamIdAndMetricTypeAndRepositoryAndDateBetween(userIds, 7L, MetricType.DAILY_COMMITS_COUNT, repo, horizon, TO);
    }

    @Test
    void getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndDateBetween_fromBeforeHorizon_isClampedToHorizon() {
        List<Long> userIds = List.of(1L, 2L);
        LocalDate horizon = LocalDate.of(2024, 1, 15);
        when(retentionPolicy.horizon()).thenReturn(horizon);
        when(repository.findByUserIdsAndTeamIdAndMetricTypeAndDateBetween(userIds, 7L, MetricType.DAILY_COMMITS_COUNT, horizon, TO))
                .thenReturn(List.of());

        service.getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndDateBetween(userIds, 7L, MetricType.DAILY_COMMITS_COUNT, FROM, TO);

        verify(repository).findByUserIdsAndTeamIdAndMetricTypeAndDateBetween(userIds, 7L, MetricType.DAILY_COMMITS_COUNT, horizon, TO);
    }
}
