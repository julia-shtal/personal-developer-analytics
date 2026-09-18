package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.user.model.AuthorIdentity;
import com.juliashtal.devanalytics.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the population of {@link MetricType#PR_SIZE_COMPLEXITY_SCORE}: enriched merged pull
 * requests only.
 *
 * <p>The merged-PR query is shared with the metrics that count every merged PR, so the
 * restriction lives in the calculator and nothing but a test holds it there. An unenriched
 * PR carries additions and deletions of zero as a placeholder, and a zero admitted to the
 * median is an observation that was never measured.</p>
 */
@ExtendWith(MockitoExtension.class)
class PrSizeComplexityCalculatorTest {

    @Mock GitHubPullRequestRepository pullRequestRepository;
    @Mock GitRepositoryEntityRepository gitRepoRepository;
    @Mock MetricSnapshotWriter writer;
    @InjectMocks PrSizeComplexityCalculator calculator;

    private static final AuthorIdentity ALICE = new AuthorIdentity(Set.of("alice@example.com"), 101L, null);
    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO   = LocalDate.of(2026, 6, 30);
    private static final long REPO_ID = 10L;

    private User user;
    private MetricCalcContext ctx;
    private GitRepositoryEntity repo;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("alice");

        ctx = new MetricCalcContext(
                user, null, List.of(REPO_ID), ALICE,
                FROM.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
                TO.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
                FROM, TO);

        repo = new GitRepositoryEntity();
        repo.setId(REPO_ID);
    }

    private GitHubPullRequestEntity pr(int additions, int deletions, int commits, StatsStatus status) {
        GitHubPullRequestEntity pr = new GitHubPullRequestEntity();
        pr.setRepository(repo);
        pr.setAuthorGithubId(101L);
        pr.setMerged(true);
        pr.setMergedAt(Instant.parse("2026-06-10T12:00:00Z"));
        pr.setAdditions(additions);
        pr.setDeletions(deletions);
        pr.setCommitsCount(commits);
        pr.setStatsStatus(status);
        return pr;
    }

    private void givenMergedPrs(GitHubPullRequestEntity... prs) {
        when(pullRequestRepository.findMergedPrsByRepoIdsAndAuthorGithubId(
                eq(List.of(REPO_ID)), eq(101L), any(), any())).thenReturn(List.of(prs));
    }

    private double savedValue() {
        ArgumentCaptor<Double> value = ArgumentCaptor.forClass(Double.class);
        verify(writer).save(eq(user), isNull(), eq(FROM),
                eq(MetricType.PR_SIZE_COMPLEXITY_SCORE), value.capture(),
                eq(repo), eq(FROM), eq(TO));
        return value.getValue();
    }

    @Test
    void calculate_enrichedPrs_savesMedianOfChangedLinesPerCommit() {
        givenMergedPrs(pr(80, 20, 5, StatsStatus.COMPLETE),    // 100 / 5 = 20
                       pr(60, 30, 3, StatsStatus.COMPLETE),    //  90 / 3 = 30
                       pr(40, 10, 1, StatsStatus.COMPLETE));   //  50 / 1 = 50
        when(gitRepoRepository.getReferenceById(REPO_ID)).thenReturn(repo);

        calculator.calculate(ctx);

        assertThat(savedValue()).isEqualTo(30.0);
    }

    @Test
    void calculate_pendingPr_isExcludedRatherThanCountedAsZero() {
        // Admitting the pending PR would give median(0, 20, 30) = 20 instead of 25.
        givenMergedPrs(pr(0, 0, 0, StatsStatus.PENDING),
                       pr(80, 20, 5, StatsStatus.COMPLETE),    // 20
                       pr(60, 30, 3, StatsStatus.COMPLETE));   // 30
        when(gitRepoRepository.getReferenceById(REPO_ID)).thenReturn(repo);

        calculator.calculate(ctx);

        assertThat(savedValue()).isEqualTo(25.0);
    }

    @Test
    void calculate_skippedAndFailedPrs_areExcluded() {
        givenMergedPrs(pr(0, 0, 0, StatsStatus.SKIPPED),
                       pr(0, 0, 0, StatsStatus.FAILED),
                       pr(90, 10, 2, StatsStatus.COMPLETE));    // 100 / 2 = 50
        when(gitRepoRepository.getReferenceById(REPO_ID)).thenReturn(repo);

        calculator.calculate(ctx);

        assertThat(savedValue()).isEqualTo(50.0);
    }

    @Test
    void calculate_everyMergedPrUnenriched_savesNothing() {
        // The same outcome as a window with no merged PRs: a value of 0.0 would read as
        // single-line commits rather than as an absent measurement.
        givenMergedPrs(pr(0, 0, 0, StatsStatus.PENDING),
                       pr(0, 0, 0, StatsStatus.PENDING));

        calculator.calculate(ctx);

        verifyNoInteractions(writer);
    }

    @Test
    void calculate_squashMergeWithZeroCommits_countsAsOneCommit() {
        givenMergedPrs(pr(30, 20, 0, StatsStatus.COMPLETE));    // 50 / max(0,1) = 50
        when(gitRepoRepository.getReferenceById(REPO_ID)).thenReturn(repo);

        calculator.calculate(ctx);

        assertThat(savedValue()).isEqualTo(50.0);
    }

    @Test
    void calculate_noMergedPrs_savesNothing() {
        givenMergedPrs();

        calculator.calculate(ctx);

        verifyNoInteractions(writer);
    }

    @Test
    void calculate_noGithubIdentity_skips() {
        MetricCalcContext noIdentity = new MetricCalcContext(
                user, null, List.of(REPO_ID),
                new AuthorIdentity(Set.of("alice@example.com"), null, null),
                ctx.from(), ctx.to(), FROM, TO);

        calculator.calculate(noIdentity);

        verifyNoInteractions(pullRequestRepository, writer);
    }

    @Test
    void calculate_emptyRepoIds_skips() {
        MetricCalcContext emptyCtx = new MetricCalcContext(
                user, null, List.of(), ALICE, ctx.from(), ctx.to(), FROM, TO);

        calculator.calculate(emptyCtx);

        verifyNoInteractions(pullRequestRepository, writer);
    }

    @Test
    void produces_returnsPrSizeComplexity() {
        assertThat(calculator.produces()).containsExactly(MetricType.PR_SIZE_COMPLEXITY_SCORE);
    }
}
