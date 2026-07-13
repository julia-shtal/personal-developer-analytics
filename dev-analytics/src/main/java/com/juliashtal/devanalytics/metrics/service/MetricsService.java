package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.metrics.calc.MetricCalcContext;
import com.juliashtal.devanalytics.metrics.calc.MetricCalculatorRegistry;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Orchestrates metric calculation across the registered calculators for a user or team scope.
 */
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MetricCalculatorRegistry metricCalculatorRegistry;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final RepoScopeResolver repoScopeResolver;

    /** Personal metrics — uses only the user's own data sources, saved with team=null. */
    @Transactional
    public void calculateDailyMetrics(Long userId, LocalDate fromDate, LocalDate toDate) {
        User user = userRepository.getReferenceById(userId);
        calculateDailyMetricsForUser(user, null, fromDate, toDate);
    }

    /**
     * Team-scoped metrics — uses only repos belonging to this specific team,
     * attributed by author identity. Saved with team=team so they are isolated
     * from the user's personal metrics and from other teams.
     */
    @Transactional
    public void calculateForTeam(Long teamId, Long requestingUserId, LocalDate fromDate, LocalDate toDate) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + teamId));

        Role role = userRepository.getReferenceById(requestingUserId).getRole();
        if (role != Role.ADMIN && !team.getManager().getId().equals(requestingUserId)) {
            throw new ForbiddenException("Only the team manager or an admin can trigger team calculation");
        }

        for (User member : team.getMembers()) {
            calculateDailyMetricsForUser(member, team, fromDate, toDate);
        }
    }

    private void calculateDailyMetricsForUser(User user, Team team, LocalDate fromDate, LocalDate toDate) {
        Instant from = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to   = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Long> repoIds = repoScopeResolver.resolve(user, team);
        MetricCalcContext ctx = new MetricCalcContext(user, team, repoIds, from, to, fromDate, toDate);

        metricCalculatorRegistry.all().forEach(c -> c.calculate(ctx));
    }
}
