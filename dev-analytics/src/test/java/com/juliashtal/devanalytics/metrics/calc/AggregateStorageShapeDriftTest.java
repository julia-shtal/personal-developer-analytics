package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPrReviewRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.metrics.MetricSnapshotRepository;
import com.juliashtal.devanalytics.metrics.model.*;
import com.juliashtal.devanalytics.metrics.service.AggregateWindowResolver;
import com.juliashtal.devanalytics.user.model.AuthorIdentity;
import com.juliashtal.devanalytics.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.juliashtal.devanalytics.metrics.model.MetricType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins which metric types are written in AGGREGATE shape, by running every registered
 * calculator against a fixture rich enough that all of them produce a snapshot and
 * recording which ones set {@code periodFrom}.
 *
 * <p>This is the test that would have caught TASK 01. The {@code aggregatePeriod} flag on
 * {@link MetricType} marked five types; thirteen were actually stored with a period, and
 * every read path that routed on the flag silently returned nothing for the other eight.
 * Asserting the shape against a checked-in expected set — rather than against the flag —
 * means the disagreement cannot recur unnoticed: adding a calculator that writes a period
 * fails here until the type is given a reduction in {@link AggregateWindowResolver}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AggregateStorageShapeDriftTest {

    @Mock MetricSnapshotRepository snapshotRepository;
    @Mock GitCommitEntityRepository commitRepository;
    @Mock GitHubPullRequestRepository pullRequestRepository;
    @Mock GitHubPrReviewRepository prReviewRepository;
    @Mock IssueRepository issueRepository;
    @Mock GitRepositoryEntityRepository gitRepoRepository;

    /**
     * The metric types stored with {@code periodFrom}/{@code periodTo}. Checked in
     * deliberately: it is the contract the read paths resolve against, and it is not
     * derivable from {@code MetricType.aggregatePeriod}, which covers only the five
     * types whose grain is the ISO calendar week.
     */
    private static final Set<MetricType> EXPECTED_PERIOD_STORED = Set.of(
            PR_LEAD_TIME_HOURS_MEDIAN,
            PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN,
            ISSUE_LEAD_TIME_HOURS_MEDIAN,
            REVIEW_RESPONSE_TIME_HOURS_MEDIAN,
            REVIEW_PARTICIPATION_COUNT,
            AFTER_HOURS_COMMIT_RATIO,
            REFACTOR_RATIO,
            MERGE_WITHOUT_REVIEW_RATIO,
            PR_SIZE_COMPLEXITY_SCORE,
            WIP_OPEN_PR_AGE_HOURS_MEDIAN,
            DEEP_WORK_STREAK_DAYS,
            COMMITS_PER_WEEK_AVG,
            KNOWLEDGE_SILO_SCORE);

    private static final Long REPO_ID = 10L;
    private static final LocalDate FROM = LocalDate.of(2024, 1, 15);   // Monday
    private static final LocalDate TO   = LocalDate.of(2024, 1, 21);   // Sunday

    private GitRepositoryEntity repo;

    @BeforeEach
    void seedEveryCalculatorsInputs() {
        repo = new GitRepositoryEntity();
        repo.setId(REPO_ID);

        when(gitRepoRepository.getReferenceById(REPO_ID)).thenReturn(repo);
        when(snapshotRepository.findExisting(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Every projection mock is created before any stubbing begins: building a mock inside
        // a when(...).thenReturn(...) argument is an UnfinishedStubbing failure.

        // Commits on three consecutive days, so streak, per-week average and daily series all fire.
        List<DailyCommitsProjection> dailyCommits = List.of(
                dailyCommits(FROM, 4L, 120.0),
                dailyCommits(FROM.plusDays(1), 2L, 80.0),
                dailyCommits(FROM.plusDays(2), 5L, 40.0));
        List<DailyChurnProjection> churn = List.of(dailyChurn(FROM, 30L, 10L));
        List<CommitDetailProjection> commitDetails = List.of(
                commitDetail(instant(FROM, 22), 5, 40),              // after hours, and a refactor
                commitDetail(instant(FROM.plusDays(1), 11), 90, 3));
        List<RepoCountProjection> totalCommits = List.of(repoCount(REPO_ID, 10L));
        List<RepoCountProjection> userCommits  = List.of(repoCount(REPO_ID, 9L));
        List<DailyCountProjection> issuesCreated = List.of(dailyCount(FROM, 2L));
        List<DailyCountProjection> issuesClosed  = List.of(dailyCount(FROM, 1L));
        List<IssueLeadTimeProjection> issueLeadTimes =
                List.of(issueLeadTime(REPO_ID, instant(FROM, 9), instant(FROM.plusDays(2), 17)));
        List<DailyCountProjection> prsCreated = List.of(dailyCount(FROM, 3L));
        List<DailyCountProjection> prsMerged  = List.of(dailyCount(FROM.plusDays(1), 2L));
        List<PrLeadTimeProjection> prLeadTimes =
                List.of(prLeadTime(REPO_ID, instant(FROM, 9), instant(FROM.plusDays(1), 15)));
        List<GitHubPullRequestEntity> mergedPrs =
                List.of(pr(1L, instant(FROM, 9), instant(FROM.plusDays(1), 15), 200, 40, 4));
        List<GitHubPullRequestEntity> openPrs = List.of(pr(2L, instant(FROM, 9), null, 10, 5, 1));
        List<GitCommitEntity> prCommits = List.of(commit(instant(FROM, 8)));
        List<PrReviewTimestampProjection> firstReviews = List.of(reviewTimestamp(1L, instant(FROM, 14)));

        when(commitRepository.aggregateCommitsDailyByRepoIdsAndIdentity(any(), any(), any(), any(), any()))
                .thenReturn(dailyCommits);
        when(commitRepository.aggregateChurnDailyByRepoIdsAndIdentity(any(), any(), any(), any(), any()))
                .thenReturn(churn);
        when(commitRepository.findCommitDetailsByRepoIdsAndIdentity(any(), any(), any(), any(), any()))
                .thenReturn(commitDetails);
        when(commitRepository.countTotalCommitsByRepoIds(any(), any(), any())).thenReturn(totalCommits);
        when(commitRepository.countCommitsByRepoIdsAndIdentity(any(), any(), any(), any(), any())).thenReturn(userCommits);
        when(commitRepository.findCommitsForPr(any(), anyInt())).thenReturn(prCommits);

        when(issueRepository.aggregateIssuesCreatedDailyByRepoIdsAndIdentity(any(), any(), any(), any(), any())).thenReturn(issuesCreated);
        when(issueRepository.aggregateIssuesClosedDailyByRepoIdsAndIdentity(any(), any(), any(), any(), any())).thenReturn(issuesClosed);
        when(issueRepository.findIssueLeadTimesByRepoIdsAndIdentity(any(), any(), any(), any(), any())).thenReturn(issueLeadTimes);

        when(pullRequestRepository.aggregatePrCreatedDailyByRepoIdsAndAuthorGithubId(any(), any(), any(), any()))
                .thenReturn(prsCreated);
        when(pullRequestRepository.aggregatePrMergedDailyByRepoIdsAndAuthorGithubId(any(), any(), any(), any()))
                .thenReturn(prsMerged);
        when(pullRequestRepository.findMergedLeadTimesByRepoIdsAndAuthorGithubId(any(), any(), any(), any()))
                .thenReturn(prLeadTimes);
        when(pullRequestRepository.findMergedPrsByRepoIdsAndAuthorGithubId(any(), any(), any(), any()))
                .thenReturn(mergedPrs);
        when(pullRequestRepository.findOpenPrsByRepoIdsAndAuthorGithubId(any(), any())).thenReturn(openPrs);

        when(prReviewRepository.findFirstReviewTimestampsByPrIds(any())).thenReturn(firstReviews);
        when(prReviewRepository.countDistinctPrsReviewedByUser(any(), any(), any(), any())).thenReturn(6L);
    }

    @Test
    void everyCalculator_periodStoredTypes_matchTheCheckedInSet() {
        Set<MetricType> periodStored = runAllCalculators().stream()
                .filter(s -> s.getPeriodFrom() != null)
                .map(MetricSnapshot::getMetricType)
                .collect(Collectors.toSet());

        assertThat(periodStored).containsExactlyInAnyOrderElementsOf(EXPECTED_PERIOD_STORED);
    }

    @Test
    void everyCalculator_dailyStoredTypes_carryNoPeriod() {
        List<MetricSnapshot> written = runAllCalculators();

        Set<MetricType> dailyStored = written.stream()
                .filter(s -> s.getPeriodFrom() == null)
                .map(MetricSnapshot::getMetricType)
                .collect(Collectors.toSet());

        // The fixture exercises every calculator, so the two shapes together must cover
        // every type and must not overlap: a type writes one shape or the other, never both.
        assertThat(dailyStored).doesNotContainAnyElementsOf(EXPECTED_PERIOD_STORED);
        assertThat(written.stream().map(MetricSnapshot::getMetricType).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(MetricType.values());
    }

    @Test
    void periodStoredTypes_allHaveAReductionDeclared() {
        AggregateWindowResolver resolver = new AggregateWindowResolver();

        assertThat(EXPECTED_PERIOD_STORED)
                .allSatisfy(type -> assertThat(resolver.reductionOf(type))
                        .as("no reduction declared for period-stored %s — reads would combine it blindly", type)
                        .isPresent());

        Set<MetricType> declared = resolver.periodStoredTypes();
        assertThat(declared)
                .as("AggregateWindowResolver declares a reduction for a type no calculator stores with a period")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_PERIOD_STORED);
    }

    // ------------------------------------------------------------------
    // harness
    // ------------------------------------------------------------------

    private List<MetricSnapshot> runAllCalculators() {
        User user = new User();
        user.setId(1L);
        user.setEmail("dev@example.com");
        user.setGithubLogin("devuser");
        user.setGithubUserId(101L);
        user.setTimezone("UTC");

        // Every identifier is populated so that all seventeen calculators clear their identity
        // guard: this test asserts storage shape, and a calculator that skipped for want of an
        // identity would silently drop out of the drift check.
        AuthorIdentity identity = new AuthorIdentity(Set.of("dev@example.com"), 101L, "jira-acct-1");

        MetricCalcContext ctx = new MetricCalcContext(
                user, null, List.of(REPO_ID), identity,
                FROM.atStartOfDay(ZoneOffset.UTC).toInstant(),
                TO.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                FROM, TO);

        buildRegistry().all().forEach(c -> c.calculate(ctx));

        org.mockito.ArgumentCaptor<MetricSnapshot> captor =
                org.mockito.ArgumentCaptor.forClass(MetricSnapshot.class);
        org.mockito.Mockito.verify(snapshotRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private MetricCalculatorRegistry buildRegistry() {
        MetricSnapshotWriter writer = new MetricSnapshotWriter(snapshotRepository);
        return new MetricCalculatorRegistry(List.of(
                new DailyCommitsCalculator(commitRepository, gitRepoRepository, writer),
                new DailyPrCalculator(pullRequestRepository, gitRepoRepository, writer),
                new DailyIssuesCalculator(issueRepository, gitRepoRepository, writer),
                new DailyChurnCalculator(commitRepository, gitRepoRepository, writer),
                new PrLeadTimeCalculator(pullRequestRepository, gitRepoRepository, writer),
                new IssueLeadTimeCalculator(issueRepository, gitRepoRepository, writer),
                new FirstCommitToMergeCalculator(pullRequestRepository, commitRepository, gitRepoRepository, writer),
                new ReviewResponseTimeCalculator(pullRequestRepository, prReviewRepository, gitRepoRepository, writer),
                new FocusRatioCalculator(commitRepository, writer),
                new AfterHoursAndRefactorCalculator(commitRepository, writer),
                new DeepWorkStreakCalculator(commitRepository, writer),
                new CommitsPerWeekCalculator(commitRepository, writer),
                new KnowledgeSiloCalculator(commitRepository, gitRepoRepository, writer),
                new PrSizeComplexityCalculator(pullRequestRepository, gitRepoRepository, writer),
                new MergeWithoutReviewCalculator(pullRequestRepository, prReviewRepository, gitRepoRepository, writer),
                new ReviewParticipationCalculator(prReviewRepository, writer),
                new WipOpenPrAgeCalculator(pullRequestRepository, gitRepoRepository, writer)
        ));
    }

    // ------------------------------------------------------------------
    // fixture builders
    // ------------------------------------------------------------------

    private static Instant instant(LocalDate day, int hour) {
        return day.atStartOfDay(ZoneOffset.UTC).plusHours(hour).toInstant();
    }

    private DailyCommitsProjection dailyCommits(LocalDate day, long count, double avgSize) {
        DailyCommitsProjection p = mock(DailyCommitsProjection.class);
        when(p.getDay()).thenReturn(Date.valueOf(day));
        when(p.getRepoId()).thenReturn(REPO_ID);
        when(p.getCommitsCount()).thenReturn(count);
        when(p.getAvgSize()).thenReturn(avgSize);
        return p;
    }

    private DailyChurnProjection dailyChurn(LocalDate day, long additions, long deletions) {
        DailyChurnProjection p = mock(DailyChurnProjection.class);
        when(p.getDay()).thenReturn(Date.valueOf(day));
        when(p.getRepoId()).thenReturn(REPO_ID);
        when(p.getAdditions()).thenReturn(additions);
        when(p.getDeletions()).thenReturn(deletions);
        return p;
    }

    private DailyCountProjection dailyCount(LocalDate day, long count) {
        DailyCountProjection p = mock(DailyCountProjection.class);
        when(p.getDay()).thenReturn(Date.valueOf(day));
        when(p.getRepoId()).thenReturn(REPO_ID);
        when(p.getCount()).thenReturn(count);
        return p;
    }

    private static CommitDetailProjection commitDetail(Instant authorDate, int additions, int deletions) {
        CommitDetailProjection p = mock(CommitDetailProjection.class);
        when(p.getAuthorDate()).thenReturn(authorDate);
        when(p.getAdditions()).thenReturn(additions);
        when(p.getDeletions()).thenReturn(deletions);
        when(p.getStatsStatus()).thenReturn(StatsStatus.COMPLETE);
        return p;
    }

    private static RepoCountProjection repoCount(Long repoId, long count) {
        RepoCountProjection p = mock(RepoCountProjection.class);
        when(p.getRepoId()).thenReturn(repoId);
        when(p.getCount()).thenReturn(count);
        return p;
    }

    private static PrLeadTimeProjection prLeadTime(Long repoId, Instant createdAt, Instant mergedAt) {
        PrLeadTimeProjection p = mock(PrLeadTimeProjection.class);
        when(p.getRepoId()).thenReturn(repoId);
        when(p.getCreatedAt()).thenReturn(createdAt);
        when(p.getMergedAt()).thenReturn(mergedAt);
        return p;
    }

    private static IssueLeadTimeProjection issueLeadTime(Long repoId, Instant createdAt, Instant closedAt) {
        IssueLeadTimeProjection p = mock(IssueLeadTimeProjection.class);
        when(p.getRepoId()).thenReturn(repoId);
        when(p.getCreatedAt()).thenReturn(createdAt);
        when(p.getClosedAt()).thenReturn(closedAt);
        return p;
    }

    private static PrReviewTimestampProjection reviewTimestamp(Long prId, Instant reviewedAt) {
        PrReviewTimestampProjection p = mock(PrReviewTimestampProjection.class);
        when(p.getPrId()).thenReturn(prId);
        when(p.getReviewedAt()).thenReturn(reviewedAt);
        return p;
    }

    private GitHubPullRequestEntity pr(Long id, Instant createdAt, Instant mergedAt,
                                       int additions, int deletions, int commitsCount) {
        GitHubPullRequestEntity pr = new GitHubPullRequestEntity();
        pr.setId(id);
        pr.setRepository(repo);
        pr.setNumber(id.intValue());
        pr.setCreatedAt(createdAt);
        pr.setMergedAt(mergedAt);
        pr.setAdditions(additions);
        pr.setDeletions(deletions);
        pr.setCommitsCount(commitsCount);
        return pr;
    }

    private static GitCommitEntity commit(Instant authorDate) {
        GitCommitEntity c = new GitCommitEntity();
        c.setAuthorDate(authorDate);
        return c;
    }
}
