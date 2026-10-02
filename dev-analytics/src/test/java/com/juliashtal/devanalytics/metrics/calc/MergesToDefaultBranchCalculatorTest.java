package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.model.PrLeadTimeProjection;
import com.juliashtal.devanalytics.metrics.repository.MetricSnapshotRepository;
import com.juliashtal.devanalytics.user.model.AuthorIdentity;
import com.juliashtal.devanalytics.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * {@link MergesToDefaultBranchCalculator} unit tests, with hand-computable fixtures: every
 * merge date below is a known day of a known ISO week, so expected weekly counts are derived
 * by inspection, not recomputed by the test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MergesToDefaultBranchCalculatorTest {

    @Mock GitHubPullRequestRepository pullRequestRepository;
    @Mock GitRepositoryEntityRepository gitRepoRepository;
    @Mock MetricSnapshotRepository snapshotRepository;

    MergesToDefaultBranchCalculator calculator;
    MetricSnapshotWriter writer;

    private static final Long REPO_ID = 10L;
    private static final Long OTHER_REPO_ID = 20L;
    private static final Long GITHUB_USER_ID = 101L;

    // Monday 2024-01-15 through Sunday 2024-01-21 is ISO week 2024-W03.
    // Monday 2024-01-22 through Sunday 2024-01-28 is ISO week 2024-W04.
    private static final LocalDate FROM = LocalDate.of(2024, 1, 15);
    private static final LocalDate TO   = LocalDate.of(2024, 1, 28);

    private GitRepositoryEntity repo;
    private GitRepositoryEntity otherRepo;
    private MetricCalcContext ctx;

    @BeforeEach
    void setUp() {
        writer = new MetricSnapshotWriter(snapshotRepository);
        calculator = new MergesToDefaultBranchCalculator(pullRequestRepository, gitRepoRepository, writer);

        repo = new GitRepositoryEntity();
        repo.setId(REPO_ID);
        otherRepo = new GitRepositoryEntity();
        otherRepo.setId(OTHER_REPO_ID);
        when(gitRepoRepository.getReferenceById(REPO_ID)).thenReturn(repo);
        when(gitRepoRepository.getReferenceById(OTHER_REPO_ID)).thenReturn(otherRepo);

        User user = new User();
        user.setId(1L);
        AuthorIdentity identity = new AuthorIdentity(Set.of(), GITHUB_USER_ID, null);
        ctx = new MetricCalcContext(user, null, List.of(REPO_ID, OTHER_REPO_ID), identity,
                FROM.atStartOfDay(ZoneOffset.UTC).toInstant(),
                TO.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                FROM, TO);
    }

    @Test
    void calculate_noRepos_writesNothing() {
        MetricCalcContext empty = new MetricCalcContext(ctx.user(), null, List.of(), ctx.identity(),
                ctx.from(), ctx.to(), ctx.fromDate(), ctx.toDate());

        calculator.calculate(empty);

        verifyNoInteractions(pullRequestRepository);
        verify(snapshotRepository, never()).upsert(any(), any(), any(), any(), any(), anyDouble(), any(), any());
    }

    @Test
    void calculate_noGithubIdentity_writesNothing() {
        AuthorIdentity noGithub = new AuthorIdentity(Set.of("dev@example.com"), null, null);
        MetricCalcContext noIdentity = new MetricCalcContext(ctx.user(), null, ctx.repoIds(), noGithub,
                ctx.from(), ctx.to(), ctx.fromDate(), ctx.toDate());

        calculator.calculate(noIdentity);

        verifyNoInteractions(pullRequestRepository);
    }

    @Test
    void calculate_noMergesInWindow_writesNothing() {
        when(pullRequestRepository.findMergedToDefaultBranchByRepoIdsAndAuthorGithubId(
                eq(ctx.repoIds()), eq(GITHUB_USER_ID), eq(ctx.from()), eq(ctx.to())))
                .thenReturn(List.of());

        calculator.calculate(ctx);

        verify(snapshotRepository, never()).upsert(any(), any(), any(), any(), any(), anyDouble(), any(), any());
    }

    @Test
    void calculate_threeMergesSameWeekSameRepo_writesOneRowWithCountThree() {
        List<PrLeadTimeProjection> merges = List.of(
                merged(REPO_ID, "2024-01-15T09:00:00Z"), // Monday, W03
                merged(REPO_ID, "2024-01-17T14:00:00Z"), // Wednesday, W03
                merged(REPO_ID, "2024-01-21T23:00:00Z")  // Sunday, W03
        );
        when(pullRequestRepository.findMergedToDefaultBranchByRepoIdsAndAuthorGithubId(
                eq(ctx.repoIds()), eq(GITHUB_USER_ID), eq(ctx.from()), eq(ctx.to())))
                .thenReturn(merges);

        calculator.calculate(ctx);

        verify(snapshotRepository).upsert(
                eq(1L), isNull(), eq(REPO_ID), eq(LocalDate.of(2024, 1, 15)),
                eq("MERGES_TO_DEFAULT_BRANCH_PER_WEEK"), eq(3.0),
                eq(LocalDate.of(2024, 1, 15)), eq(LocalDate.of(2024, 1, 21)));
    }

    @Test
    void calculate_mergesInTwoDifferentWeeks_writesTwoRows() {
        List<PrLeadTimeProjection> merges = List.of(
                merged(REPO_ID, "2024-01-15T09:00:00Z"), // W03
                merged(REPO_ID, "2024-01-22T09:00:00Z")  // W04
        );
        when(pullRequestRepository.findMergedToDefaultBranchByRepoIdsAndAuthorGithubId(
                eq(ctx.repoIds()), eq(GITHUB_USER_ID), eq(ctx.from()), eq(ctx.to())))
                .thenReturn(merges);

        calculator.calculate(ctx);

        verify(snapshotRepository).upsert(eq(1L), isNull(), eq(REPO_ID), eq(LocalDate.of(2024, 1, 15)),
                eq("MERGES_TO_DEFAULT_BRANCH_PER_WEEK"), eq(1.0),
                eq(LocalDate.of(2024, 1, 15)), eq(LocalDate.of(2024, 1, 21)));
        verify(snapshotRepository).upsert(eq(1L), isNull(), eq(REPO_ID), eq(LocalDate.of(2024, 1, 22)),
                eq("MERGES_TO_DEFAULT_BRANCH_PER_WEEK"), eq(1.0),
                eq(LocalDate.of(2024, 1, 22)), eq(LocalDate.of(2024, 1, 28)));
    }

    @Test
    void calculate_mergesAcrossTwoRepos_writesOneRowPerRepo() {
        List<PrLeadTimeProjection> merges = List.of(
                merged(REPO_ID, "2024-01-15T09:00:00Z"),
                merged(OTHER_REPO_ID, "2024-01-16T09:00:00Z")
        );
        when(pullRequestRepository.findMergedToDefaultBranchByRepoIdsAndAuthorGithubId(
                eq(ctx.repoIds()), eq(GITHUB_USER_ID), eq(ctx.from()), eq(ctx.to())))
                .thenReturn(merges);

        calculator.calculate(ctx);

        verify(snapshotRepository).upsert(eq(1L), isNull(), eq(REPO_ID), eq(LocalDate.of(2024, 1, 15)),
                eq("MERGES_TO_DEFAULT_BRANCH_PER_WEEK"), eq(1.0),
                eq(LocalDate.of(2024, 1, 15)), eq(LocalDate.of(2024, 1, 21)));
        verify(snapshotRepository).upsert(eq(1L), isNull(), eq(OTHER_REPO_ID), eq(LocalDate.of(2024, 1, 15)),
                eq("MERGES_TO_DEFAULT_BRANCH_PER_WEEK"), eq(1.0),
                eq(LocalDate.of(2024, 1, 15)), eq(LocalDate.of(2024, 1, 21)));
    }

    @Test
    void produces_returnsOnlyMergesToDefaultBranchPerWeek() {
        assertThat(calculator.produces()).containsExactly(MetricType.MERGES_TO_DEFAULT_BRANCH_PER_WEEK);
    }

    private static PrLeadTimeProjection merged(Long repoId, String mergedAtIso) {
        PrLeadTimeProjection p = mock(PrLeadTimeProjection.class);
        when(p.getRepoId()).thenReturn(repoId);
        when(p.getMergedAt()).thenReturn(Instant.parse(mergedAtIso));
        return p;
    }
}
