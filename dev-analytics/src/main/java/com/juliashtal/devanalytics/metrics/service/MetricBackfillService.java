package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.metrics.MetricCoverageRepository;
import com.juliashtal.devanalytics.metrics.model.BackfillProperties;
import com.juliashtal.devanalytics.metrics.model.BackfillResult;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Computes personal metrics for days in a user's history that were collected but never calculated.
 *
 * <p>Missing days are the range from the earliest collected activity to yesterday, minus the
 * {@code metric_coverage} ledger; because coverage records actual computation rather than a
 * watermark, {@code maxDaysPerRun} throttles a run without truncating it. Days are processed
 * newest-first in contiguous blocks, and every write goes through
 * {@link MetricsService#calculateDailyMetrics}, which owns both the upsert guard and the ledger.</p>
 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(BackfillProperties.class)
@Slf4j
public class MetricBackfillService implements MetricBackfillTrigger {

    private final UserRepository userRepository;
    private final RepoScopeResolver repoScopeResolver;
    private final GitCommitEntityRepository commitRepository;
    private final GitHubPullRequestRepository pullRequestRepository;
    private final IssueRepository issueRepository;
    private final MetricCoverageRepository coverageRepository;
    private final UserMetricsPurger metricsPurger;
    private final MetricsService metricsService;
    private final BackfillProperties properties;

    /**
     * Users with a backfill pass running. Guards the read-then-write snapshot save, which has no
     * unique constraint behind it, against two runs inserting the same day. In-process only.
     */
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * Computes up to {@code maxDaysPerRun} missing days for one user, newest first.
     *
     * <p>Deliberately not {@code @Transactional}: each block commits through
     * {@link MetricsService#calculateDailyMetrics}, so a failed block rolls back only its own
     * work. CPU- and database-bound, so never call it from a request thread; a concurrent call
     * for the same user returns {@link BackfillResult#empty()}.</p>
     */
    public BackfillResult backfillUser(Long userId) {
        if (!inFlight.add(userId)) {
            log.info("Backfill already in flight for userId={}, skipping this run", userId);
            return BackfillResult.empty();
        }
        try {
            return runBackfill(userId);
        } finally {
            inFlight.remove(userId);
        }
    }

    private BackfillResult runBackfill(Long userId) {
        Coverage coverage = coverage(userId);
        if (coverage.missing().isEmpty()) {
            return coverage.asResult(0, 0);
        }

        // Clamped, not trusted: a non-positive cap would throw out of subList rather than no-op.
        int batchSize = Math.max(0, Math.min(properties.maxDaysPerRun(), coverage.missing().size()));
        List<LocalDate> batch = coverage.missing().subList(0, batchSize);

        long computed = 0;
        for (Block block : contiguousBlocks(batch)) {
            try {
                metricsService.calculateDailyMetrics(userId, block.from(), block.to());
                computed += block.days();
                log.debug("Backfilled userId={}: {} -> {}", userId, block.from(), block.to());
            } catch (RuntimeException e) {
                log.warn("Backfill block failed for userId={} ({} -> {})",
                        userId, block.from(), block.to(), e);
            }
        }

        long remaining = coverage.missing().size() - computed;
        if (batch.isEmpty()) {
            log.error("Backfill cap for userId={} is {}, so no missing day can ever be computed; "
                            + "{} day(s) remain uncovered.",
                    userId, properties.maxDaysPerRun(), remaining);
        } else if (computed == 0) {
            log.error("Backfill made no progress for userId={}: every block of the batch {} -> {} "
                            + "failed. The next run assembles the same batch, so a reproducible "
                            + "failure stalls this user's coverage and hides all {} older missing days.",
                    userId, batch.get(batch.size() - 1), batch.get(0), remaining);
        } else {
            log.info("Backfill for userId={} computed {} day(s), {} remaining",
                    userId, computed, remaining);
        }
        return coverage.asResult(computed, remaining);
    }

    /** Read-only view of the target range and what is still missing; safe on a request thread. */
    @Transactional(readOnly = true)
    public BackfillResult describeCoverage(Long userId) {
        Coverage coverage = coverage(userId);
        return coverage.asResult(0, coverage.missing().size());
    }

    /**
     * {@inheritDoc}
     *
     * <p>The reset commits before anything is recomputed, so a failed backfill leaves the user
     * with no coverage until later runs rebuild it. Logged at {@code ERROR} and rethrown.</p>
     */
    @Override
    public void onFirstCollection(Long userId) {
        coverageRepository.deleteByUserId(userId);
        log.info("First collection for userId={}: coverage reset, running backfill", userId);
        try {
            backfillUser(userId);
        } catch (RuntimeException e) {
            log.error("Coverage was reset for userId={} but the follow-up backfill failed. "
                    + "The user has no coverage until later runs rebuild it.", userId, e);
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Purge and recomputation are separate transactions, as in {@link #onFirstCollection}.
     * Between them the user has no metrics, which is the honest state after an identity change.</p>
     */
    @Override
    public void onAttributionChanged(Long userId) {
        metricsPurger.purge(userId);
        log.info("Attribution changed for userId={}: snapshots and coverage cleared, running backfill", userId);
        try {
            backfillUser(userId);
        } catch (RuntimeException e) {
            log.error("Snapshots and coverage were cleared for userId={} but the follow-up backfill "
                    + "failed. The user has no metrics until later runs rebuild them.", userId, e);
            throw e;
        }
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /** An inclusive run of consecutive missing days, computed in a single calculator pass. */
    private record Block(LocalDate from, LocalDate to) {
        long days() {
            // ChronoUnit, not Period: Period.getDays() drops the month component across a boundary.
            return ChronoUnit.DAYS.between(from, to) + 1L;
        }
    }

    /** Target range and the missing days inside it, newest first. */
    private record Coverage(LocalDate from, LocalDate to, List<LocalDate> missing) {
        BackfillResult asResult(long computed, long remaining) {
            return from == null
                    ? BackfillResult.empty()
                    : new BackfillResult(computed, remaining, from, to);
        }
    }

    /** Derives the target range and its missing days, so both entry points share one definition. */
    private Coverage coverage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        // Same entitlement path the calculators use, so scope never disagrees.
        List<Long> repoIds = repoScopeResolver.resolve(user, null);
        if (repoIds.isEmpty()) {
            return new Coverage(null, null, List.of());
        }

        Optional<Instant> earliest = earliestActivity(repoIds);
        if (earliest.isEmpty()) {
            return new Coverage(null, null, List.of());
        }

        ZoneId zone = zoneOf(user);
        LocalDate from = LocalDate.ofInstant(earliest.get(), zone);
        LocalDate to = LocalDate.now(zone).minusDays(1);
        if (from.isAfter(to)) {
            return new Coverage(null, null, List.of());
        }

        Set<LocalDate> covered = new HashSet<>(coverageRepository.findDatesInRange(userId, from, to));
        List<LocalDate> missing = new ArrayList<>();
        for (LocalDate day = to; !day.isBefore(from); day = day.minusDays(1)) {
            if (!covered.contains(day)) {
                missing.add(day);
            }
        }
        return new Coverage(from, to, missing);
    }

    /** The oldest of the three collected-activity timestamps, or empty when nothing was collected. */
    private Optional<Instant> earliestActivity(List<Long> repoIds) {
        return Stream.of(
                        commitRepository.findEarliestAuthorDate(repoIds),
                        pullRequestRepository.findEarliestCreatedAt(repoIds),
                        issueRepository.findEarliestCreatedAt(repoIds))
                .flatMap(Optional::stream)
                .min(Instant::compareTo);
    }

    /**
     * Day boundaries follow the user's own zone, matching the after-hours calculator. An unset or
     * unparseable zone falls back to UTC rather than failing the run.
     */
    private static ZoneId zoneOf(User user) {
        String timezone = user.getTimezone();
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException e) {
            log.warn("Unparseable timezone '{}' for userId={}, falling back to UTC",
                    timezone, user.getId());
            return ZoneOffset.UTC;
        }
    }

    /** Splits a descending day list into contiguous blocks, newest first, one calculator pass each. */
    private static List<Block> contiguousBlocks(List<LocalDate> descendingDays) {
        if (descendingDays.isEmpty()) {
            return List.of();
        }
        List<Block> blocks = new ArrayList<>();
        LocalDate blockEnd = descendingDays.get(0);
        LocalDate blockStart = blockEnd;
        for (int i = 1; i < descendingDays.size(); i++) {
            LocalDate day = descendingDays.get(i);
            if (day.equals(blockStart.minusDays(1))) {
                blockStart = day;
            } else {
                blocks.add(new Block(blockStart, blockEnd));
                blockEnd = day;
                blockStart = day;
            }
        }
        blocks.add(new Block(blockStart, blockEnd));
        return blocks;
    }
}
