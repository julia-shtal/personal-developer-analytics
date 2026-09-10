package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.metrics.MetricCoverageRepository;
import com.juliashtal.devanalytics.metrics.calc.MetricCalcContext;
import com.juliashtal.devanalytics.metrics.calc.MetricCalculator;
import com.juliashtal.devanalytics.metrics.calc.MetricCalculatorRegistry;
import com.juliashtal.devanalytics.user.model.AuthorIdentity;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.user.service.AuthorIdentityResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
    private final AuthorIdentityResolver authorIdentityResolver;
    private final MetricCoverageRepository coverageRepository;

    /** Personal metrics — uses only the user's own data sources, saved with team=null. */
    @Transactional
    public void calculateDailyMetrics(Long userId, LocalDate fromDate, LocalDate toDate) {
        User user = userRepository.getReferenceById(userId);
        calculateDailyMetricsForUser(user, null, fromDate, toDate);
    }

    /**
     * Team-scoped metrics — only repos belonging to this team, saved with team=team so they stay
     * isolated from personal metrics and from other teams.
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

    /**
     * Runs the registry in two shapes, because the table stores two.
     *
     * <p>Series calculators write one row per calendar day in a single pass; aggregate
     * calculators write one row per ISO week the range touches, windowed Monday to Sunday, so
     * each calculator still sees one window and never learns about the grain. Also the sole
     * writer of the {@code metric_coverage} ledger, inside this transaction so coverage cannot
     * drift from what was computed.</p>
     */
    private void calculateDailyMetricsForUser(User user, Team team, LocalDate fromDate, LocalDate toDate) {
        List<Long> repoIds = repoScopeResolver.resolve(user, team);
        // Resolved once per run, not per calculator: re-reading it per week multiplies queries.
        AuthorIdentity identity = authorIdentityResolver.resolve(user);

        List<MetricCalculator> seriesCalculators = new ArrayList<>();
        List<MetricCalculator> aggregateCalculators = new ArrayList<>();
        for (MetricCalculator calculator : metricCalculatorRegistry.all()) {
            if (writesAggregatePeriod(calculator)) {
                aggregateCalculators.add(calculator);
            } else {
                seriesCalculators.add(calculator);
            }
        }

        if (!seriesCalculators.isEmpty()) {
            MetricCalcContext fullRange = context(user, team, repoIds, identity, fromDate, toDate);
            seriesCalculators.forEach(c -> c.calculate(fullRange));
        }

        if (!aggregateCalculators.isEmpty()) {
            for (LocalDate weekStart : isoWeeksOverlapping(fromDate, toDate)) {
                MetricCalcContext week = context(user, team, repoIds, identity, weekStart, weekStart.plusDays(6));
                aggregateCalculators.forEach(c -> c.calculate(week));
            }
        }

        // Personal path only, and skipped on an empty scope: calculators return early there, so
        // marking those days would tell the backfill a user's history was already covered.
        if (team == null && !repoIds.isEmpty()) {
            for (LocalDate day = fromDate; !day.isAfter(toDate); day = day.plusDays(1)) {
                coverageRepository.markCovered(user.getId(), day);
            }
        }
    }

    private static boolean writesAggregatePeriod(MetricCalculator calculator) {
        return calculator.produces().stream().anyMatch(t -> t.aggregatePeriod);
    }

    private static MetricCalcContext context(User user, Team team, List<Long> repoIds,
                                             AuthorIdentity identity,
                                             LocalDate fromDate, LocalDate toDate) {
        Instant from = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to   = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new MetricCalcContext(user, team, repoIds, identity, from, to, fromDate, toDate);
    }

    /**
     * The Monday of every ISO week the inclusive range touches. Weeks are always whole, so a
     * stored period never claims narrower coverage than was computed.
     */
    private static List<LocalDate> isoWeeksOverlapping(LocalDate fromDate, LocalDate toDate) {
        List<LocalDate> weeks = new ArrayList<>();
        LocalDate weekStart = fromDate.with(DayOfWeek.MONDAY);
        LocalDate lastWeekStart = toDate.with(DayOfWeek.MONDAY);
        while (!weekStart.isAfter(lastWeekStart)) {
            weeks.add(weekStart);
            weekStart = weekStart.plusWeeks(1);
        }
        return weeks;
    }
}
