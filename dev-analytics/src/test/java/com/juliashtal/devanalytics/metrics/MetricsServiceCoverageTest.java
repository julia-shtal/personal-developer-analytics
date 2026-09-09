package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.metrics.calc.MetricCalculatorRegistry;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.metrics.service.RepoScopeResolver;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Proves that {@link MetricsService} records coverage only on the personal calculation path
 * and only when the resolved repo scope is non-empty, so the {@code metric_coverage} ledger
 * the backfill reads can never claim a day as computed when it was not.
 */
@ExtendWith(MockitoExtension.class)
class MetricsServiceCoverageTest {

    @Mock MetricCalculatorRegistry registry;
    @Mock UserRepository userRepository;
    @Mock TeamRepository teamRepository;
    @Mock RepoScopeResolver repoScopeResolver;
    @Mock MetricCoverageRepository coverageRepository;

    MetricsService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new MetricsService(registry, userRepository, teamRepository,
                repoScopeResolver, coverageRepository);
        user = new User();
        user.setId(1L);
        when(registry.all()).thenReturn(List.of());
    }

    @Test
    void calculateDailyMetrics_personalScopeWithRepos_marksEveryDayCovered() {
        when(userRepository.getReferenceById(1L)).thenReturn(user);
        when(repoScopeResolver.resolve(user, null)).thenReturn(List.of(7L));

        service.calculateDailyMetrics(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3));

        verify(coverageRepository).markCovered(1L, LocalDate.of(2026, 3, 1));
        verify(coverageRepository).markCovered(1L, LocalDate.of(2026, 3, 2));
        verify(coverageRepository).markCovered(1L, LocalDate.of(2026, 3, 3));
        verifyNoMoreInteractions(coverageRepository);
    }

    @Test
    void calculateDailyMetrics_noReposInScope_marksNothing() {
        when(userRepository.getReferenceById(1L)).thenReturn(user);
        when(repoScopeResolver.resolve(user, null)).thenReturn(List.of());

        service.calculateDailyMetrics(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3));

        verify(coverageRepository, never()).markCovered(any(), any());
    }

    @Test
    void calculateForTeam_teamScope_marksNothing() {
        Team team = new Team();
        team.setId(9L);
        team.setManager(user);
        team.setMembers(java.util.Set.of(user));
        when(teamRepository.findById(9L)).thenReturn(java.util.Optional.of(team));
        when(userRepository.getReferenceById(1L)).thenReturn(user);
        when(repoScopeResolver.resolve(user, team)).thenReturn(List.of(7L));

        service.calculateForTeam(9L, 1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3));

        verify(coverageRepository, never()).markCovered(any(), any());
    }
}
