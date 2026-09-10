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

    /**
     * Runs the registry in two shapes, because the table stores two.
     *
     * <p>Series calculators write one row per calendar day and take a single pass over
     * the whole range. Aggregate calculators write one row carrying
     * {@code periodFrom}/{@code periodTo}, and run once per ISO calendar week the range
     * touches — each pass windowed on that week's Monday to Sunday. The ISO week is the
     * canonical grain: it matches the weekly summary job and {@code COMMITS_PER_WEEK_AVG},
     * and is the smallest window over which a median of PR lead times is not usually a
     * median of one observation.
     *
     * <p>Before this split, an aggregate row's period was whatever range the caller
     * happened to pass, so the nightly job stored one-day windows that no weekly read
     * could resolve. The branch lives here rather than inside individual calculators so
     * each calculator still sees one window and does not know about the grain.
     *
     * <p>This method is also the sole writer of the {@code metric_coverage} ledger, which
     * records the calendar days personal metrics have actually been computed for and is the
     * reference {@code MetricBackfillService} subtracts its target range from. The write is
     * deliberately co-located with the calculation and inside the same transaction, so
     * coverage cannot drift from what was computed; see the guard's own comment below for
     * why team scopes and empty repo scopes are excluded.
     */
    private void calculateDailyMetricsForUser(User user, Team team, LocalDate fromDate, LocalDate toDate) {
        List<Long> repoIds = repoScopeResolver.resolve(user, team);
        // Resolved once per run rather than per calculator: it is the same for all seventeen,
        // and re-reading it per weekly window would multiply the query count by the range.
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

        // Coverage is recorded here, on the personal path only, so the ledger the backfill
        // reads can never disagree with what was actually computed — whichever entry point
        // triggered it. Skipped when the scope is empty: every calculator returns early on
        // an empty repo list, so marking those days would let the nightly job convince the
        // backfill that a user's history is covered before any repository was attached.
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
     * The Monday of every ISO week the inclusive range touches. Weeks are always whole,
     * so a partial request still produces a full-week window and the stored period never
     * claims a narrower coverage than was computed.
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
