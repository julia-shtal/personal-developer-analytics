package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.metrics.model.BackfillProperties;
import com.juliashtal.devanalytics.metrics.model.BackfillResult;
import com.juliashtal.devanalytics.metrics.service.MetricBackfillService;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.metrics.service.RepoScopeResolver;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the resumable history backfill.
 *
 * <p>The fake ledger below is the point of the fixture: it grows only when
 * {@code calculateDailyMetrics} is invoked, exactly as {@code MetricsService} marks coverage in
 * production. Successive runs therefore resume honestly, and the convergence property — a
 * fourth run over ninety days of history does nothing at all — can be asserted rather than
 * assumed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetricBackfillServiceTest {

    @Mock UserRepository userRepository;
    @Mock RepoScopeResolver repoScopeResolver;
    @Mock GitCommitEntityRepository commitRepository;
    @Mock GitHubPullRequestRepository pullRequestRepository;
    @Mock IssueRepository issueRepository;
    @Mock MetricCoverageRepository coverageRepository;
    @Mock MetricsService metricsService;

    MetricBackfillService service;
    User user;

    /** Days the fake ledger holds; only {@code calculateDailyMetrics} adds to it. */
    private final Set<LocalDate> ledger = new HashSet<>();

    private static final ZoneId UTC = ZoneId.of("UTC");
    private LocalDate today;

    @BeforeEach
    void setUp() {
        service = newService(30);

        today = LocalDate.now(UTC);

        user = new User();
        user.setId(1L);
        user.setTimezone("UTC");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(repoScopeResolver.resolve(user, null)).thenReturn(List.of(7L));
        when(commitRepository.findEarliestAuthorDate(List.of(7L))).thenReturn(Optional.empty());
        when(pullRequestRepository.findEarliestCreatedAt(List.of(7L))).thenReturn(Optional.empty());
        when(issueRepository.findEarliestCreatedAt(List.of(7L))).thenReturn(Optional.empty());

        when(coverageRepository.findDatesInRange(anyLong(), any(), any()))
                .thenAnswer(inv -> {
                    LocalDate from = inv.getArgument(1);
                    LocalDate to = inv.getArgument(2);
                    return ledger.stream()
                            .filter(d -> !d.isBefore(from) && !d.isAfter(to))
                            .sorted().toList();
                });

        doAnswer(inv -> {
            LocalDate from = inv.getArgument(1);
            LocalDate to = inv.getArgument(2);
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) ledger.add(d);
            return null;
        }).when(metricsService).calculateDailyMetrics(anyLong(), any(), any());
    }

    /** A service configured with the given per-run cap; the cap is immutable after construction. */
    private MetricBackfillService newService(int maxDaysPerRun) {
        return new MetricBackfillService(userRepository, repoScopeResolver, commitRepository,
                pullRequestRepository, issueRepository, coverageRepository, metricsService,
                new BackfillProperties(maxDaysPerRun));
    }

    /** Commits reach back {@code days} days before today, so the target range is {@code days} long. */
    private void commitsSpanning(int days) {
        LocalDate earliest = today.minusDays(days);
        when(commitRepository.findEarliestAuthorDate(List.of(7L)))
                .thenReturn(Optional.of(earliest.atStartOfDay(UTC).toInstant()));
    }

    private void coverEntireRange(int days) {
        for (LocalDate d = today.minusDays(days); !d.isAfter(today.minusDays(1)); d = d.plusDays(1)) {
            ledger.add(d);
        }
    }

    @Test
    void backfillUser_ninetyDaysNoCoverage_computesThirtyNewestFirst() {
        commitsSpanning(90);

        BackfillResult result = service.backfillUser(1L);

        assertThat(result.daysComputed()).isEqualTo(30);
        assertThat(result.daysRemaining()).isEqualTo(60);
        assertThat(result.coverageFrom()).isEqualTo(today.minusDays(90));
        assertThat(result.coverageTo()).isEqualTo(today.minusDays(1));

        // Newest-first: the block computed is the 30 days ending yesterday.
        verify(metricsService).calculateDailyMetrics(
                1L, today.minusDays(30), today.minusDays(1));
        verifyNoMoreInteractions(metricsService);
    }

    @Test
    void backfillUser_successiveRuns_resumeUntilNoOp() {
        commitsSpanning(90);

        assertThat(service.backfillUser(1L).daysComputed()).isEqualTo(30);
        assertThat(service.backfillUser(1L).daysComputed()).isEqualTo(30);

        BackfillResult third = service.backfillUser(1L);
        assertThat(third.daysComputed()).isEqualTo(30);
        assertThat(third.daysRemaining()).isZero();

        // Every day of the ninety-day range is now covered — the cap deferred days rather
        // than dropping them.
        assertThat(ledger).hasSize(90)
                .contains(today.minusDays(90), today.minusDays(45), today.minusDays(1));

        clearInvocations(metricsService);
        BackfillResult fourth = service.backfillUser(1L);
        assertThat(fourth.daysComputed()).isZero();
        assertThat(fourth.daysRemaining()).isZero();
        verifyNoInteractions(metricsService);
    }

    @Test
    void backfillUser_successiveRuns_computeNewestBlockFirst() {
        commitsSpanning(90);

        InOrder inOrder = inOrder(metricsService);
        service.backfillUser(1L);
        service.backfillUser(1L);
        service.backfillUser(1L);

        inOrder.verify(metricsService).calculateDailyMetrics(1L, today.minusDays(30), today.minusDays(1));
        inOrder.verify(metricsService).calculateDailyMetrics(1L, today.minusDays(60), today.minusDays(31));
        inOrder.verify(metricsService).calculateDailyMetrics(1L, today.minusDays(90), today.minusDays(61));
    }

    @Test
    void backfillUser_holeInMiddleOfRange_fillsTheHole() {
        commitsSpanning(10);
        coverEntireRange(10);
        ledger.remove(today.minusDays(6));
        ledger.remove(today.minusDays(5));
        ledger.remove(today.minusDays(4));

        BackfillResult result = service.backfillUser(1L);

        assertThat(result.daysComputed()).isEqualTo(3);
        assertThat(result.daysRemaining()).isZero();
        verify(metricsService).calculateDailyMetrics(
                1L, today.minusDays(6), today.minusDays(4));
        verifyNoMoreInteractions(metricsService);
    }

    @Test
    void backfillUser_nonContiguousMissingDays_computesSeparateBlocksNewestFirst() {
        commitsSpanning(10);
        coverEntireRange(10);
        ledger.remove(today.minusDays(8));
        ledger.remove(today.minusDays(3));

        BackfillResult result = service.backfillUser(1L);

        assertThat(result.daysComputed()).isEqualTo(2);
        InOrder inOrder = inOrder(metricsService);
        inOrder.verify(metricsService).calculateDailyMetrics(1L, today.minusDays(3), today.minusDays(3));
        inOrder.verify(metricsService).calculateDailyMetrics(1L, today.minusDays(8), today.minusDays(8));
        verifyNoMoreInteractions(metricsService);
    }

    @Test
    void backfillUser_blockFails_computesRemainingBlocksAndReportsItStillMissing() {
        commitsSpanning(10);
        coverEntireRange(10);
        ledger.remove(today.minusDays(8));
        ledger.remove(today.minusDays(3));
        doThrow(new IllegalStateException("calculator blew up"))
                .when(metricsService).calculateDailyMetrics(1L, today.minusDays(3), today.minusDays(3));

        BackfillResult result = service.backfillUser(1L);

        // The failing block does not abort the run, and is not counted as computed — so the
        // next run still sees it as missing instead of the whole user stalling on one bad day.
        assertThat(result.daysComputed()).isEqualTo(1);
        assertThat(result.daysRemaining()).isEqualTo(1);
        verify(metricsService).calculateDailyMetrics(1L, today.minusDays(8), today.minusDays(8));
        assertThat(ledger).contains(today.minusDays(8)).doesNotContain(today.minusDays(3));
    }

    @Test
    void backfillUser_alreadyFullyCovered_recomputesNothing() {
        commitsSpanning(10);
        coverEntireRange(10);

        BackfillResult result = service.backfillUser(1L);

        assertThat(result.daysComputed()).isZero();
        assertThat(result.daysRemaining()).isZero();
        assertThat(result.coverageFrom()).isEqualTo(today.minusDays(10));
        verifyNoInteractions(metricsService);
    }

    @Test
    void backfillUser_rerunAfterCoverageCleared_recomputesTheSameRange() {
        commitsSpanning(10);

        BackfillResult first = service.backfillUser(1L);
        ledger.clear();
        clearInvocations(metricsService);
        BackfillResult second = service.backfillUser(1L);

        assertThat(second).isEqualTo(first);
        verify(metricsService).calculateDailyMetrics(1L, today.minusDays(10), today.minusDays(1));
    }

    @Test
    void backfillUser_noActivityAtAll_writesNothing() {
        BackfillResult result = service.backfillUser(1L);

        assertThat(result.daysComputed()).isZero();
        assertThat(result.daysRemaining()).isZero();
        assertThat(result.coverageFrom()).isNull();
        assertThat(result.coverageTo()).isNull();
        verify(metricsService, never()).calculateDailyMetrics(anyLong(), any(), any());
    }

    @Test
    void backfillUser_noReposInScope_writesNothing() {
        when(repoScopeResolver.resolve(user, null)).thenReturn(List.of());

        BackfillResult result = service.backfillUser(1L);

        assertThat(result.daysComputed()).isZero();
        assertThat(result.coverageFrom()).isNull();
        verify(metricsService, never()).calculateDailyMetrics(anyLong(), any(), any());
    }

    @Test
    void backfillUser_activityOnlyToday_writesNothing() {
        // Earliest activity is today, but the range ends yesterday: from > to, nothing to cover.
        when(commitRepository.findEarliestAuthorDate(List.of(7L)))
                .thenReturn(Optional.of(today.atStartOfDay(UTC).toInstant()));

        BackfillResult result = service.backfillUser(1L);

        assertThat(result.daysComputed()).isZero();
        assertThat(result.coverageFrom()).isNull();
        verifyNoInteractions(metricsService);
    }

    @Test
    void backfillUser_earliestActivityIsTheOldestOfThreeSources_usesIt() {
        LocalDate issueDay = today.minusDays(40);
        when(commitRepository.findEarliestAuthorDate(List.of(7L)))
                .thenReturn(Optional.of(today.minusDays(5).atStartOfDay(UTC).toInstant()));
        when(pullRequestRepository.findEarliestCreatedAt(List.of(7L)))
                .thenReturn(Optional.of(today.minusDays(20).atStartOfDay(UTC).toInstant()));
        when(issueRepository.findEarliestCreatedAt(List.of(7L)))
                .thenReturn(Optional.of(issueDay.atStartOfDay(UTC).toInstant()));

        BackfillResult result = service.backfillUser(1L);

        assertThat(result.coverageFrom()).isEqualTo(issueDay);
        assertThat(result.daysComputed() + result.daysRemaining()).isEqualTo(40);
    }

    @Test
    void backfillUser_userTimezoneAheadOfUtc_usesUserZoneForDayBoundary() {
        // 2026-03-01T12:00Z is 2026-03-02 01:00 in Auckland, so Auckland's earliest day is
        // the 2nd while Los Angeles sees 2026-03-01 04:00 and starts on the 1st.
        Instant earliest = Instant.parse("2026-03-01T12:00:00Z");
        when(commitRepository.findEarliestAuthorDate(List.of(7L))).thenReturn(Optional.of(earliest));

        user.setTimezone("Pacific/Auckland");
        BackfillResult auckland = service.backfillUser(1L);

        ledger.clear();
        user.setTimezone("America/Los_Angeles");
        BackfillResult losAngeles = service.backfillUser(1L);

        assertThat(auckland.coverageFrom()).isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(losAngeles.coverageFrom()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void backfillUser_unparseableTimezone_fallsBackToUtc() {
        user.setTimezone("Not/AZone");
        when(commitRepository.findEarliestAuthorDate(List.of(7L)))
                .thenReturn(Optional.of(Instant.parse("2026-03-01T12:00:00Z")));

        assertThat(service.backfillUser(1L).coverageFrom()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void backfillUser_nullTimezone_fallsBackToUtc() {
        user.setTimezone(null);
        when(commitRepository.findEarliestAuthorDate(List.of(7L)))
                .thenReturn(Optional.of(Instant.parse("2026-03-01T12:00:00Z")));

        assertThat(service.backfillUser(1L).coverageFrom()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void backfillUser_configuredCap_boundsTheRun() {
        service = newService(7);
        commitsSpanning(90);

        BackfillResult result = service.backfillUser(1L);

        assertThat(result.daysComputed()).isEqualTo(7);
        assertThat(result.daysRemaining()).isEqualTo(83);
        verify(metricsService).calculateDailyMetrics(1L, today.minusDays(7), today.minusDays(1));
    }

    @Test
    void backfillUser_partialWeekBlock_isPassedThroughForIsoWeekExpansion() {
        // Aggregate-period metrics must land on whole ISO weeks (TASK 01). The backfill does
        // not week-align anything itself: it hands the contiguous day block to
        // MetricsService.calculateDailyMetrics, which already expands the range to every ISO
        // week it touches for the aggregate calculators and keeps day grain for the rest.
        // Duplicating that split here would give the two paths two chances to disagree.
        commitsSpanning(3);

        service.backfillUser(1L);

        verify(metricsService).calculateDailyMetrics(1L, today.minusDays(3), today.minusDays(1));
        verifyNoMoreInteractions(metricsService);
    }

    @Test
    void backfillUser_capOfZero_computesNothingWithoutFailing() {
        // @Min(1) on BackfillProperties rejects this at startup, so it is unreachable in a
        // running application. Asserted anyway because the empty batch it produces used to
        // escape as IndexOutOfBoundsException from the block grouping, which a nightly job
        // would have swallowed to a warning with the feature silently dead.
        service = newService(0);
        commitsSpanning(10);

        BackfillResult result = service.backfillUser(1L);

        assertThat(result.daysComputed()).isZero();
        assertThat(result.daysRemaining()).isEqualTo(10);
        verifyNoInteractions(metricsService);
    }

    @Test
    void backfillUser_negativeCap_computesNothingWithoutFailing() {
        service = newService(-1);
        commitsSpanning(10);

        BackfillResult result = service.backfillUser(1L);

        assertThat(result.daysComputed()).isZero();
        assertThat(result.daysRemaining()).isEqualTo(10);
        verifyNoInteractions(metricsService);
    }

    @Test
    void backfillProperties_capBelowOne_failsValidation() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            assertThat(validator.validate(new BackfillProperties(0)))
                    .singleElement()
                    .satisfies(v -> assertThat(v.getPropertyPath()).hasToString("maxDaysPerRun"));
            assertThat(validator.validate(new BackfillProperties(1))).isEmpty();
            assertThat(validator.validate(new BackfillProperties(30))).isEmpty();
        }
    }

    @Test
    void backfillUser_blockSpanningMonthBoundary_countsEveryDayInIt() {
        // Fixed dates on purpose. Period.between(2026-01-31, 2026-03-02) is P1M2D, so a
        // regression from ChronoUnit back to Period.getDays() reports 3 days instead of 31.
        // Dates derived from LocalDate.now() only expose that on the handful of calendar days
        // where the block happens to straddle a month this way, so the guard would sleep.
        LocalDate holeFrom = LocalDate.of(2026, 1, 31);
        LocalDate holeTo = LocalDate.of(2026, 3, 2);
        LocalDate rangeStart = LocalDate.of(2025, 12, 1);

        service = newService(60);
        when(commitRepository.findEarliestAuthorDate(List.of(7L)))
                .thenReturn(Optional.of(rangeStart.atStartOfDay(UTC).toInstant()));
        for (LocalDate d = rangeStart; !d.isAfter(today.minusDays(1)); d = d.plusDays(1)) {
            ledger.add(d);
        }
        for (LocalDate d = holeFrom; !d.isAfter(holeTo); d = d.plusDays(1)) {
            ledger.remove(d);
        }

        BackfillResult result = service.backfillUser(1L);

        assertThat(result.daysComputed()).isEqualTo(31);
        assertThat(result.daysRemaining()).isZero();
        verify(metricsService).calculateDailyMetrics(1L, holeFrom, holeTo);
        verifyNoMoreInteractions(metricsService);
    }

    @Test
    void backfillUser_onlyBlockFailsRepeatedly_makesNoProgressOnEitherRun() {
        // The documented limitation of skip-and-continue: a fresh user's missing set is fully
        // contiguous, so the batch is a single block. When that block fails reproducibly the
        // run computes nothing and the next run assembles an identical batch. Asserted so the
        // behaviour is pinned rather than assumed, and logged at ERROR by the service.
        commitsSpanning(10);
        doThrow(new IllegalStateException("calculator blew up"))
                .when(metricsService).calculateDailyMetrics(1L, today.minusDays(10), today.minusDays(1));

        BackfillResult first = service.backfillUser(1L);
        BackfillResult second = service.backfillUser(1L);

        assertThat(first.daysComputed()).isZero();
        assertThat(first.daysRemaining()).isEqualTo(10);
        assertThat(second).isEqualTo(first);
        assertThat(ledger).isEmpty();
    }

    @Test
    void backfillUser_runAlreadyInFlightForSameUser_isSkipped() throws Exception {
        // Guards MetricSnapshotWriter's read-then-write against the scheduler and a first
        // collection landing together: metric_snapshots has no unique constraint, so two
        // concurrent runs over the same days would both insert.
        commitsSpanning(10);
        CountDownLatch insideCalculation = new CountDownLatch(1);
        CountDownLatch releaseCalculation = new CountDownLatch(1);
        doAnswer(inv -> {
            insideCalculation.countDown();
            assertThat(releaseCalculation.await(5, TimeUnit.SECONDS)).isTrue();
            LocalDate from = inv.getArgument(1);
            LocalDate to = inv.getArgument(2);
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) ledger.add(d);
            return null;
        }).when(metricsService).calculateDailyMetrics(anyLong(), any(), any());

        Thread firstRun = new Thread(() -> service.backfillUser(1L), "backfill-first-run");
        firstRun.start();
        assertThat(insideCalculation.await(5, TimeUnit.SECONDS)).isTrue();

        BackfillResult concurrent = service.backfillUser(1L);

        releaseCalculation.countDown();
        firstRun.join(5_000);

        assertThat(concurrent.daysComputed()).isZero();
        assertThat(concurrent.coverageFrom()).isNull();
        verify(metricsService, times(1)).calculateDailyMetrics(anyLong(), any(), any());
        assertThat(ledger).hasSize(10);
    }

    @Test
    void backfillUser_afterAnEarlierRunFinished_isNotSkipped() {
        // The in-flight guard must release, or the first run would poison every later one.
        commitsSpanning(10);

        service.backfillUser(1L);
        ledger.clear();
        clearInvocations(metricsService);
        BackfillResult second = service.backfillUser(1L);

        assertThat(second.daysComputed()).isEqualTo(10);
        verify(metricsService).calculateDailyMetrics(1L, today.minusDays(10), today.minusDays(1));
    }

    @Test
    void backfillUser_blankTimezone_fallsBackToUtc() {
        user.setTimezone("   ");
        when(commitRepository.findEarliestAuthorDate(List.of(7L)))
                .thenReturn(Optional.of(Instant.parse("2026-03-01T12:00:00Z")));

        assertThat(service.backfillUser(1L).coverageFrom()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void describeCoverage_doesNotCompute() {
        commitsSpanning(90);

        BackfillResult result = service.describeCoverage(1L);

        assertThat(result.daysRemaining()).isEqualTo(90);
        assertThat(result.daysComputed()).isZero();
        assertThat(result.coverageFrom()).isEqualTo(today.minusDays(90));
        assertThat(result.coverageTo()).isEqualTo(today.minusDays(1));
        verify(metricsService, never()).calculateDailyMetrics(anyLong(), any(), any());
    }

    @Test
    void onFirstCollection_clearsCoverageThenBackfills() {
        commitsSpanning(10);
        ledger.addAll(List.of(today.minusDays(2), today.minusDays(3)));
        doAnswer(inv -> { ledger.clear(); return null; })
                .when(coverageRepository).deleteByUserId(1L);

        service.onFirstCollection(1L);

        InOrder inOrder = inOrder(coverageRepository, metricsService);
        inOrder.verify(coverageRepository).deleteByUserId(1L);
        inOrder.verify(metricsService).calculateDailyMetrics(
                1L, today.minusDays(10), today.minusDays(1));
    }

    @Test
    void onFirstCollection_backfillFails_propagatesAfterTheResetHasCommitted() {
        // The reset and the recomputation are not one unit of work. If the second step fails
        // the user is left with no coverage, so the failure must surface rather than leaving
        // the destructive half silently applied.
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.onFirstCollection(1L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("User not found: 1");

        verify(coverageRepository).deleteByUserId(1L);
        verifyNoInteractions(metricsService);
    }
}
