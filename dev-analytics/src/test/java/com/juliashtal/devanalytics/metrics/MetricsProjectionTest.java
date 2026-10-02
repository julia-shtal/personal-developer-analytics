package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.metrics.calc.AfterHoursAndRefactorCalculator;
import com.juliashtal.devanalytics.metrics.calc.DailyChurnCalculator;
import com.juliashtal.devanalytics.metrics.calc.DailyCommitsCalculator;
import com.juliashtal.devanalytics.metrics.calc.KnowledgeSiloCalculator;
import com.juliashtal.devanalytics.metrics.calc.MetricCalcContext;
import com.juliashtal.devanalytics.metrics.calc.MetricSnapshotWriter;
import com.juliashtal.devanalytics.metrics.model.*;
import com.juliashtal.devanalytics.metrics.repository.MetricSnapshotRepository;
import com.juliashtal.devanalytics.user.model.AuthorIdentity;
import com.juliashtal.devanalytics.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Verifies that each metric calculator produces correct numeric values from its projection.
 * Tests are isolated per calculator — no dependency on MetricsService dispatch.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetricsProjectionTest {

    @Mock
    MetricSnapshotRepository snapshotRepository;
    @Mock GitCommitEntityRepository commitRepository;
    @Mock GitRepositoryEntityRepository gitRepoRepository;

    static final Long USER_ID = 1L;
    static final Long REPO_ID = 10L;
    static final LocalDate DATE = LocalDate.of(2024, 1, 15);

    User user;
    GitRepositoryEntity repo;
    MetricSnapshotWriter writer;
    MetricCalcContext ctx;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_ID);
        user.setEmail("dev@example.com");
        user.setTimezone("UTC");

        repo = new GitRepositoryEntity();
        repo.setId(REPO_ID);

        when(gitRepoRepository.getReferenceById(REPO_ID)).thenReturn(repo);

        writer = new MetricSnapshotWriter(snapshotRepository);

        Instant from = DATE.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to   = DATE.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        // The projections under test are shape assertions, so the identity only has to be
        // non-empty enough for each calculator's guard to let it through.
        AuthorIdentity identity = new AuthorIdentity(Set.of("dev@example.com"), 101L, "jira-acct-1");
        ctx = new MetricCalcContext(user, null, List.of(REPO_ID), identity, from, to, DATE, DATE);
    }

    /**
     * Reads a metric's upserted value back off the captured invocation rather than an entity.
     * <p>{@code upsert} takes raw identity fields and a primitive {@code double}, so there is no
     * {@code MetricSnapshot} to inspect, and capturing the primitive with an {@link ArgumentCaptor}
     * would unbox a null and throw.</p>
     */
    private double capturedValue(MetricType type) {
        return mockingDetails(snapshotRepository).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("upsert"))
                .filter(inv -> type.name().equals(inv.getArgument(4)))
                .map(inv -> (double) inv.getArgument(5))
                .findFirst()
                .orElseThrow(() -> new java.util.NoSuchElementException("No upsert captured for " + type));
    }

    // ── DailyCommitsProjection ────────────────────────────────────────────────

    @Test
    void dailyCommitsProjection_countsAndAvgSizeSavedCorrectly() {
        DailyCommitsProjection row = mock(DailyCommitsProjection.class);
        when(row.getDay()).thenReturn(Date.valueOf(DATE));
        when(row.getRepoId()).thenReturn(REPO_ID);
        when(row.getCommitsCount()).thenReturn(7L);
        when(row.getAvgSize()).thenReturn(42.0);
        when(commitRepository.aggregateCommitsDailyByRepoIdsAndIdentity(anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(row));

        new DailyCommitsCalculator(commitRepository, gitRepoRepository, writer).calculate(ctx);

        assertThat(capturedValue(MetricType.DAILY_COMMITS_COUNT)).isEqualTo(7.0);
        assertThat(capturedValue(MetricType.DAILY_COMMITS_AVG_SIZE)).isEqualTo(42.0);
    }

    @Test
    void dailyCommitsProjection_nullAvgSize_savedAsZero() {
        // What the conditional aggregate returns when no commit that day was enriched.
        DailyCommitsProjection row = mock(DailyCommitsProjection.class);
        when(row.getDay()).thenReturn(Date.valueOf(DATE));
        when(row.getRepoId()).thenReturn(REPO_ID);
        when(row.getCommitsCount()).thenReturn(3L);
        when(row.getAvgSize()).thenReturn(null);
        when(commitRepository.aggregateCommitsDailyByRepoIdsAndIdentity(anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(row));

        new DailyCommitsCalculator(commitRepository, gitRepoRepository, writer).calculate(ctx);

        assertThat(capturedValue(MetricType.DAILY_COMMITS_AVG_SIZE)).isEqualTo(0.0);
    }

    // ── DailyChurnProjection ──────────────────────────────────────────────────

    @Test
    void dailyChurnProjection_ratioCalculatedCorrectly() {
        DailyChurnProjection row = mock(DailyChurnProjection.class);
        when(row.getDay()).thenReturn(Date.valueOf(DATE));
        when(row.getRepoId()).thenReturn(REPO_ID);
        when(row.getAdditions()).thenReturn(30L);
        when(row.getDeletions()).thenReturn(10L);   // churn = 10 / (30+10) = 0.25
        when(commitRepository.aggregateChurnDailyByRepoIdsAndIdentity(anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(row));

        new DailyChurnCalculator(commitRepository, gitRepoRepository, writer).calculate(ctx);

        assertThat(capturedValue(MetricType.DAILY_CHURN_RATIO)).isEqualTo(0.25);
    }

    // ── RepoCountProjection ───────────────────────────────────────────────────

    @Test
    void repoCountProjection_knowledgeSiloCalculatedCorrectly() {
        RepoCountProjection total = mock(RepoCountProjection.class);
        when(total.getRepoId()).thenReturn(REPO_ID);
        when(total.getCount()).thenReturn(100L);

        RepoCountProjection mine = mock(RepoCountProjection.class);
        when(mine.getRepoId()).thenReturn(REPO_ID);
        when(mine.getCount()).thenReturn(60L);   // share = 60/100 = 0.6

        when(commitRepository.countTotalCommitsByRepoIds(anyList(), any(), any())).thenReturn(List.of(total));
        when(commitRepository.countCommitsByRepoIdsAndIdentity(anyList(), any(), any(), any(), any())).thenReturn(List.of(mine));

        new KnowledgeSiloCalculator(commitRepository, gitRepoRepository, writer).calculate(ctx);

        assertThat(capturedValue(MetricType.KNOWLEDGE_SILO_SCORE)).isEqualTo(0.6);
    }

    // ── CommitDetailProjection ────────────────────────────────────────────────

    @Test
    void commitDetailProjection_afterHoursRatioCalculatedCorrectly() {
        // 20:00 UTC on a Monday — outside 09-18 → counts as after-hours
        Instant eveningCommit = Instant.parse("2024-01-15T20:00:00Z");

        CommitDetailProjection row = mock(CommitDetailProjection.class);
        when(row.getAuthorDate()).thenReturn(eveningCommit);
        when(row.getAdditions()).thenReturn(10);
        when(row.getDeletions()).thenReturn(5);
        when(row.getStatsStatus()).thenReturn(StatsStatus.COMPLETE);
        when(commitRepository.findCommitDetailsByRepoIdsAndIdentity(anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(row));

        new AfterHoursAndRefactorCalculator(commitRepository, writer).calculate(ctx);

        assertThat(capturedValue(MetricType.AFTER_HOURS_COMMIT_RATIO)).isEqualTo(1.0);  // 1 out-of-hours / 1 total
    }

    @Test
    void commitDetailProjection_refactorRatioCalculatedCorrectly() {
        // deletions(20) > additions(10) → refactor commit
        Instant workHoursCommit = Instant.parse("2024-01-15T10:00:00Z");

        CommitDetailProjection row = mock(CommitDetailProjection.class);
        when(row.getAuthorDate()).thenReturn(workHoursCommit);
        when(row.getAdditions()).thenReturn(10);
        when(row.getDeletions()).thenReturn(20);
        when(row.getStatsStatus()).thenReturn(StatsStatus.COMPLETE);
        when(commitRepository.findCommitDetailsByRepoIdsAndIdentity(anyList(), any(), any(), any(), any()))
                .thenReturn(List.of(row));

        new AfterHoursAndRefactorCalculator(commitRepository, writer).calculate(ctx);

        assertThat(capturedValue(MetricType.REFACTOR_RATIO)).isEqualTo(1.0);  // 1 refactor / 1 enriched
    }
}
