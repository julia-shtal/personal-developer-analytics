package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.metrics.calc.MetricCalcContext;
import com.juliashtal.devanalytics.metrics.calc.MetricCalculator;
import com.juliashtal.devanalytics.metrics.calc.MetricCalculatorRegistry;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.metrics.service.RepoScopeResolver;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock MetricCalculatorRegistry metricCalculatorRegistry;
    @Mock UserRepository userRepository;
    @Mock TeamRepository teamRepository;
    @Mock RepoScopeResolver repoScopeResolver;

    @InjectMocks MetricsService metricsService;

    private static final LocalDate FROM = LocalDate.of(2024, 1, 15);
    private static final LocalDate TO   = LocalDate.of(2024, 1, 21);

    // ------------------------------------------------------------------
    // Personal path: calculateDailyMetrics
    // ------------------------------------------------------------------

    @Test
    void calculateDailyMetrics_invokesAllCalculators() {
        User user = new User();
        user.setId(1L);
        when(userRepository.getReferenceById(1L)).thenReturn(user);
        when(repoScopeResolver.resolve(user, null)).thenReturn(List.of(10L));

        MetricCalculator calculator = mock(MetricCalculator.class);
        when(metricCalculatorRegistry.all()).thenReturn(List.of(calculator));

        metricsService.calculateDailyMetrics(1L, FROM, TO);

        verify(metricCalculatorRegistry).all();
        verify(calculator).calculate(any(MetricCalcContext.class));
    }

    @Test
    void calculateDailyMetrics_contextCarriesCorrectDates() {
        User user = new User();
        user.setId(1L);
        when(userRepository.getReferenceById(1L)).thenReturn(user);
        when(repoScopeResolver.resolve(user, null)).thenReturn(List.of(10L));

        ArgumentCaptor<MetricCalcContext> ctxCaptor = ArgumentCaptor.forClass(MetricCalcContext.class);
        MetricCalculator calculator = mock(MetricCalculator.class);
        when(metricCalculatorRegistry.all()).thenReturn(List.of(calculator));

        metricsService.calculateDailyMetrics(1L, FROM, TO);

        verify(calculator).calculate(ctxCaptor.capture());
        MetricCalcContext ctx = ctxCaptor.getValue();
        assertThat(ctx.fromDate()).isEqualTo(FROM);
        assertThat(ctx.toDate()).isEqualTo(TO);
        assertThat(ctx.user()).isSameAs(user);
        assertThat(ctx.team()).isNull();
    }

    // ------------------------------------------------------------------
    // Team path: calculateForTeam — authorization and member loop
    // ------------------------------------------------------------------

    @Test
    void calculateForTeam_throwsWhenTeamNotFound() {
        when(teamRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> metricsService.calculateForTeam(99L, 1L, FROM, TO))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Team not found: 99");

        verify(metricCalculatorRegistry, never()).all();
    }

    @Test
    void calculateForTeam_deniesNonManagerNonAdmin() {
        User manager = user(1L, Role.MANAGER);
        User requester = user(2L, Role.DEVELOPER);
        Team team = team(50L, manager, Set.of(manager));

        when(teamRepository.findById(50L)).thenReturn(Optional.of(team));
        when(userRepository.getReferenceById(2L)).thenReturn(requester);

        assertThatThrownBy(() -> metricsService.calculateForTeam(50L, 2L, FROM, TO))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("team manager or an admin");

        verify(metricCalculatorRegistry, never()).all();
    }

    @Test
    void calculateForTeam_allowsAdminEvenIfNotManager() {
        User manager = user(1L, Role.MANAGER);
        User admin = user(2L, Role.ADMIN);
        User member = user(3L, Role.DEVELOPER);
        Team team = team(50L, manager, Set.of(member));

        when(teamRepository.findById(50L)).thenReturn(Optional.of(team));
        when(userRepository.getReferenceById(2L)).thenReturn(admin);
        when(repoScopeResolver.resolve(member, team)).thenReturn(List.of(10L));

        MetricCalculator calculator = mock(MetricCalculator.class);
        when(metricCalculatorRegistry.all()).thenReturn(List.of(calculator));

        metricsService.calculateForTeam(50L, 2L, FROM, TO);

        verify(calculator).calculate(any(MetricCalcContext.class));
    }

    @Test
    void calculateForTeam_allowsManagerOfTeam() {
        User manager = user(1L, Role.MANAGER);
        User member = user(3L, Role.DEVELOPER);
        Team team = team(50L, manager, Set.of(member));

        when(teamRepository.findById(50L)).thenReturn(Optional.of(team));
        when(userRepository.getReferenceById(1L)).thenReturn(manager);
        when(repoScopeResolver.resolve(member, team)).thenReturn(List.of(10L));

        MetricCalculator calculator = mock(MetricCalculator.class);
        when(metricCalculatorRegistry.all()).thenReturn(List.of(calculator));

        metricsService.calculateForTeam(50L, 1L, FROM, TO);

        verify(calculator).calculate(any(MetricCalcContext.class));
    }

    @Test
    void calculateForTeam_calculatesForEveryMemberWithTeamScopedContext() {
        User manager = user(1L, Role.MANAGER);
        User memberA = user(3L, Role.DEVELOPER);
        User memberB = user(4L, Role.DEVELOPER);
        Team team = team(50L, manager, Set.of(memberA, memberB));

        when(teamRepository.findById(50L)).thenReturn(Optional.of(team));
        when(userRepository.getReferenceById(1L)).thenReturn(manager);
        when(repoScopeResolver.resolve(memberA, team)).thenReturn(List.of(10L));
        when(repoScopeResolver.resolve(memberB, team)).thenReturn(List.of(20L));

        ArgumentCaptor<MetricCalcContext> ctxCaptor = ArgumentCaptor.forClass(MetricCalcContext.class);
        MetricCalculator calculator = mock(MetricCalculator.class);
        when(metricCalculatorRegistry.all()).thenReturn(List.of(calculator));

        metricsService.calculateForTeam(50L, 1L, FROM, TO);

        // One calculate() call per member, each carrying the team-scoped context.
        verify(calculator, times(2)).calculate(ctxCaptor.capture());
        assertThat(ctxCaptor.getAllValues())
                .allSatisfy(ctx -> {
                    assertThat(ctx.team()).isSameAs(team);
                    assertThat(ctx.fromDate()).isEqualTo(FROM);
                    assertThat(ctx.toDate()).isEqualTo(TO);
                })
                .extracting(MetricCalcContext::user)
                .containsExactlyInAnyOrder(memberA, memberB);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static User user(Long id, Role role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        return u;
    }

    private static Team team(Long id, User manager, Set<User> members) {
        Team t = new Team();
        t.setId(id);
        t.setManager(manager);
        t.setMembers(members);
        return t;
    }
}