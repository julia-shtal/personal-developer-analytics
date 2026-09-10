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
 * <p>The target range runs from the earliest collected activity (oldest commit, PR, or issue)
 * to yesterday. Earliest activity is used as the coverage reference instead of {@code sync_jobs}
 * because it reflects the same data the calculators read.
 *
 * <p>Missing days = target range minus the {@code metric_coverage} ledger. Since coverage reflects
 * actual computation rather than a watermark, {@code maxDaysPerRun} is a resumable throttle: a run
 * that stops early leaves gaps that later runs will revisit. The old watermark-based approach
 * turned this cap into permanent truncation, leaving unrevisited holes.
 *
 * <p>Missing days are processed newest-first (so the dashboard's visible range fills first) and in
 * contiguous blocks (one calculator pass per range, not per day). Aggregate-period metrics need no
 * extra handling: {@link MetricsService#calculateDailyMetrics} already expands ranges to full ISO
 * weeks for those types.
 *
 * <p>All writes go through {@link MetricsService#calculateDailyMetrics}, which owns both the
 * snapshot upsert guard and the coverage ledger — this service never marks coverage itself.
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
    private final MetricsService metricsService;
    private final BackfillProperties properties;

    /**
     * Users with a backfill pass currently running, preventing the nightly scheduler and a
     * first collection from computing the same days concurrently.
     *
     * <p>Needed because {@code MetricSnapshotWriter.save} is read-then-write
     * ({@code findExisting(...).orElseGet(...)}) and {@code metric_snapshots} has no unique
     * constraint (only the plain {@code V18} index). Concurrent transactions could both find
     * nothing and both insert, producing duplicates that skew aggregates. The coverage ledger
     * is unaffected either way, since {@code markCovered} is a real {@code ON CONFLICT} upsert.
     *
     * <p>In-process only — sufficient for this single-instance deployment. A multi-instance
     * setup would need a DB-level guard (e.g. {@code pg_try_advisory_lock} on user id), but
     * that's deliberately omitted since no current deployment needs it.
     */
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * Computes up to {@code maxDaysPerRun} missing days for one user, newest first.
     *
     * <p>Deliberately not {@code @Transactional}: each contiguous block commits via
     * {@link MetricsService#calculateDailyMetrics}'s own transaction, so a failed block rolls
     * back only its own snapshots and coverage marks. An enclosing transaction would break this —
     * the inner transaction would mark it rollback-only, causing the outer commit to throw
     * {@code UnexpectedRollbackException} and discard already-succeeded blocks.
     *
     * <p>A failed block is logged and skipped, letting other blocks still commit — but this only
     * helps when the batch has multiple blocks. For a new user with contiguous imported history
     * (this service's primary case), the batch is one block, so a reproducibly failing day stalls
     * the run entirely: nothing computes, the next run retries the same batch, and older days
     * stay unreachable. Such stalls are logged at {@code ERROR} for observability; splitting
     * blocks or quarantining bad days is out of scope.
     *
     * <p>CPU- and database-bound over up to a month of history — must not run on a request
     * thread. Only the nightly scheduler and async collection path call this. A concurrent call
     * for the same user returns {@link BackfillResult#empty()} without computing anything.
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

        // Clamped rather than trusted: @Min(1) already rejects a non-positive cap at startup,
        // but a negative bound here would throw out of subList and a zero one out of the
        // block-grouping, and neither belongs in a nightly job.
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

    /**
     * Read-only view of the same computation — the target range and how much of it is still
     * missing — safe to call from a request thread because it writes nothing.
     */
    @Transactional(readOnly = true)
    public BackfillResult describeCoverage(Long userId) {
        Coverage coverage = coverage(userId);
        return coverage.asResult(0, coverage.missing().size());
    }

    /**
     * {@inheritDoc}
     *
     * <p>The reset and the recomputation are two separate units of work, not one atomic step:
     * the delete commits on its own before any metric is recalculated. A backfill that then
     * fails leaves the user with no coverage at all, which later runs must rebuild from the
     * start of their history a capped batch at a time. The failure is logged at {@code ERROR}
     * and rethrown so the destructive step is traceable rather than silently half-applied.
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

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /** An inclusive run of consecutive missing days, computed in a single calculator pass. */
    private record Block(LocalDate from, LocalDate to) {
        long days() {
            // ChronoUnit, not Period: Period.getDays() returns only the day component, so a
            // block spanning a month boundary (2026-01-31 -> 2026-03-02 is P1M2D) would report
            // 2 days instead of 30 and under-count what the run actually computed.
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

    /**
     * Derives the target range and the missing days inside it. Writes nothing, so the
     * computing and the read-only entry point share one definition of "missing".
     */
    private Coverage coverage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        // Same entitlement path the calculators use, so backfill and calculation never
        // disagree about which repositories are in scope.
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
     * Day boundaries follow the user's own zone, matching how the after-hours calculator
     * interprets wall-clock time. A server-zone conversion would put the first and last day of
     * the range somewhere other than where the metrics themselves are read. An unset or
     * unparseable zone falls back to UTC rather than failing the whole run.
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

    /**
     * Splits a descending list of days into contiguous blocks, newest block first, so each
     * block becomes one calculator pass over a real range instead of one pass per day. An empty
     * input yields no blocks rather than relying on a caller to have checked.
     */
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
