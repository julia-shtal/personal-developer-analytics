package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPrReviewRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.metrics.model.*;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.metrics.service.RepoScopeResolver;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.juliashtal.devanalytics.metrics.model.MetricType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetricsServiceTest {

    @Mock MetricSnapshotRepository snapshotRepository;
    @Mock GitCommitEntityRepository commitRepository;
    @Mock GitHubPullRequestRepository pullRequestRepository;
    @Mock GitHubPrReviewRepository prReviewRepository;
    @Mock IssueRepository issueRepository;
    @Mock UserRepository userRepository;
    @Mock TeamRepository teamRepository;
    @Mock GitRepositoryEntityRepository gitRepoRepository;
    @Mock RepoScopeResolver repoScopeResolver;

    @InjectMocks MetricsService service;

    static final Long USER_ID = 1L;
    static final Long REPO_ID = 10L;
    static final LocalDate FROM = LocalDate.of(2024, 1, 15); // Monday
    static final LocalDate TO   = LocalDate.of(2024, 1, 21); // Sunday

    User user;
    GitRepositoryEntity repo;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_ID);
        user.setEmail("dev@example.com");
        user.setGithubLogin("devuser");
        user.setTimezone("Europe/Berlin");

        repo = new GitRepositoryEntity();
        repo.setId(REPO_ID);

        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(repoScopeResolver.resolve(user, null)).thenReturn(List.of(REPO_ID));
        when(gitRepoRepository.getReferenceById(REPO_ID)).thenReturn(repo);
        when(snapshotRepository.findExisting(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        lenient().when(commitRepository.aggregateCommitsDailyByRepoIdsAndAuthorEmail(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(commitRepository.aggregateChurnDailyByRepoIdsAndAuthorEmail(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(commitRepository.findCommitDetailsByRepoIdsAndAuthorEmail(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(commitRepository.countTotalCommitsByRepoIds(any(), any(), any())).thenReturn(List.of());
        lenient().when(commitRepository.countCommitsByRepoIdsAndAuthorEmail(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(pullRequestRepository.aggregatePrCreatedDailyByRepoIdsAndAuthorLogin(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(pullRequestRepository.aggregatePrMergedDailyByRepoIdsAndAuthorLogin(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(pullRequestRepository.findMergedLeadTimesByRepoIdsAndAuthorLogin(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(pullRequestRepository.findMergedPrsByRepoIdsAndAuthorLogin(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(issueRepository.aggregateIssuesCreatedDailyByRepoIds(any(), any(), any())).thenReturn(List.of());
        lenient().when(issueRepository.aggregateIssuesClosedDailyByRepoIds(any(), any(), any())).thenReturn(List.of());
        lenient().when(issueRepository.findIssueLeadTimesByRepoIds(any(), any(), any())).thenReturn(List.of());
        lenient().when(prReviewRepository.findFirstReviewTimestampsByPrIds(any())).thenReturn(List.of());
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private List<MetricSnapshot> allSaves() {
        return Mockito.mockingDetails(snapshotRepository).getInvocations().stream()
                .filter(inv -> "save".equals(inv.getMethod().getName()))
                .map(inv -> (MetricSnapshot) inv.getArguments()[0])
                .toList();
    }

    private MetricSnapshot savedOf(MetricType type) {
        return allSaves().stream()
                .filter(s -> s.getMetricType() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No snapshot saved for " + type));
    }

    private DailyCommitsProjection commitsRow(LocalDate day, long count, Double avgSize) {
        DailyCommitsProjection p = mock(DailyCommitsProjection.class);
        when(p.getDay()).thenReturn(Date.valueOf(day));
        when(p.getRepoId()).thenReturn(REPO_ID);
        when(p.getCommitsCount()).thenReturn(count);
        // skip null stub — Mockito already returns null for reference types by default
        if (avgSize != null) when(p.getAvgSize()).thenReturn(avgSize);
        return p;
    }

    private DailyCountProjection countRow(LocalDate day, long count) {
        DailyCountProjection p = mock(DailyCountProjection.class);
        when(p.getDay()).thenReturn(Date.valueOf(day));
        when(p.getRepoId()).thenReturn(REPO_ID);
        when(p.getCount()).thenReturn(count);
        return p;
    }

    private DailyChurnProjection churnRow(LocalDate day, long add, long del) {
        DailyChurnProjection p = mock(DailyChurnProjection.class);
        when(p.getDay()).thenReturn(Date.valueOf(day));
        when(p.getRepoId()).thenReturn(REPO_ID);
        when(p.getAdditions()).thenReturn(add);
        when(p.getDeletions()).thenReturn(del);
        return p;
    }

    private PrLeadTimeProjection prLeadRow(Instant created, Instant merged) {
        PrLeadTimeProjection p = mock(PrLeadTimeProjection.class);
        when(p.getRepoId()).thenReturn(REPO_ID);
        when(p.getCreatedAt()).thenReturn(created);
        when(p.getMergedAt()).thenReturn(merged);
        return p;
    }

    private IssueLeadTimeProjection issueLeadRow(Instant created, Instant closed) {
        IssueLeadTimeProjection p = mock(IssueLeadTimeProjection.class);
        when(p.getRepoId()).thenReturn(REPO_ID);
        when(p.getCreatedAt()).thenReturn(created);
        when(p.getClosedAt()).thenReturn(closed);
        return p;
    }

    private CommitDetailProjection commitDetailRow(Instant authorDate, int add, int del, StatsStatus status) {
        CommitDetailProjection p = mock(CommitDetailProjection.class);
        when(p.getAuthorDate()).thenReturn(authorDate);
        when(p.getAdditions()).thenReturn(add);
        when(p.getDeletions()).thenReturn(del);
        when(p.getStatsStatus()).thenReturn(status);
        return p;
    }

    private RepoCountProjection repoCountRow(long repoId, long count) {
        RepoCountProjection p = mock(RepoCountProjection.class);
        when(p.getRepoId()).thenReturn(repoId);
        when(p.getCount()).thenReturn(count);
        return p;
    }

    private GitHubPullRequestEntity buildPr(long id, Instant createdAt, Instant mergedAt) {
        GitHubPullRequestEntity pr = new GitHubPullRequestEntity();
        pr.setId(id);
        pr.setRepository(repo);
        pr.setNumber((int) id);
        pr.setTitle("PR-" + id);
        pr.setCreatedAt(createdAt);
        pr.setMergedAt(mergedAt);
        pr.setMerged(true);
        return pr;
    }

    // ─── DAILY_COMMITS_COUNT / DAILY_COMMITS_AVG_SIZE ──────────────────────────

    @Test
    void dailyCommits_countAndAvgSize_savedForDay() {
        var row = commitsRow(FROM, 3, 50.0);
        when(commitRepository.aggregateCommitsDailyByRepoIdsAndAuthorEmail(any(), any(), any(), any()))
                .thenReturn(List.of(row));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        MetricSnapshot count = savedOf(DAILY_COMMITS_COUNT);
        assertThat(count.getValue()).isEqualTo(3.0);
        assertThat(count.getDate()).isEqualTo(FROM);
        assertThat(count.getRepository().getId()).isEqualTo(REPO_ID);

        assertThat(savedOf(DAILY_COMMITS_AVG_SIZE).getValue()).isEqualTo(50.0);
    }

    @Test
    void dailyCommits_nullAvgSize_treatedAsZero() {
        var row = commitsRow(FROM, 1, null);
        when(commitRepository.aggregateCommitsDailyByRepoIdsAndAuthorEmail(any(), any(), any(), any()))
                .thenReturn(List.of(row));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(DAILY_COMMITS_AVG_SIZE).getValue()).isEqualTo(0.0);
    }

    // ─── DAILY_PR_CREATED / DAILY_PR_MERGED ────────────────────────────────────

    @Test
    void dailyPrs_createdAndMerged_savedPerDay() {
        var created = countRow(FROM, 2);
        var merged  = countRow(FROM, 1);
        when(pullRequestRepository.aggregatePrCreatedDailyByRepoIdsAndAuthorLogin(any(), any(), any(), any()))
                .thenReturn(List.of(created));
        when(pullRequestRepository.aggregatePrMergedDailyByRepoIdsAndAuthorLogin(any(), any(), any(), any()))
                .thenReturn(List.of(merged));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(DAILY_PR_CREATED).getValue()).isEqualTo(2.0);
        assertThat(savedOf(DAILY_PR_MERGED).getValue()).isEqualTo(1.0);
    }

    @Test
    void dailyPrs_noGithubLogin_querySkipped() {
        user.setGithubLogin(null);

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        verify(pullRequestRepository, never())
                .aggregatePrCreatedDailyByRepoIdsAndAuthorLogin(any(), any(), any(), any());
    }

    // ─── DAILY_ISSUES_CREATED / DAILY_ISSUES_CLOSED ────────────────────────────

    @Test
    void dailyIssues_createdAndClosed_savedPerDay() {
        var created = countRow(FROM, 5);
        var closed  = countRow(FROM, 3);
        when(issueRepository.aggregateIssuesCreatedDailyByRepoIds(any(), any(), any()))
                .thenReturn(List.of(created));
        when(issueRepository.aggregateIssuesClosedDailyByRepoIds(any(), any(), any()))
                .thenReturn(List.of(closed));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(DAILY_ISSUES_CREATED).getValue()).isEqualTo(5.0);
        assertThat(savedOf(DAILY_ISSUES_CLOSED).getValue()).isEqualTo(3.0);
    }

    // ─── DAILY_CHURN_RATIO ─────────────────────────────────────────────────────

    @Test
    void dailyChurn_deletionsOverTotal_correctRatio() {
        var row = churnRow(FROM, 100, 50);
        when(commitRepository.aggregateChurnDailyByRepoIdsAndAuthorEmail(any(), any(), any(), any()))
                .thenReturn(List.of(row));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        // 50 / (100 + 50) = 1/3
        assertThat(savedOf(DAILY_CHURN_RATIO).getValue()).isEqualTo(50.0 / 150.0);
    }

    @Test
    void dailyChurn_zeroAdditionsAndDeletions_ratioZero() {
        var row = churnRow(FROM, 0, 0);
        when(commitRepository.aggregateChurnDailyByRepoIdsAndAuthorEmail(any(), any(), any(), any()))
                .thenReturn(List.of(row));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(DAILY_CHURN_RATIO).getValue()).isEqualTo(0.0);
    }

    // ─── PR_LEAD_TIME_HOURS_MEDIAN ──────────────────────────────────────────────

    @Test
    void leadTimePrs_evenCount_medianIsAverageOfMiddleTwo() {
        // lead times: 10h, 20h, 30h, 40h → sorted → median = (20+30)/2 = 25h
        Instant base = Instant.parse("2024-01-15T00:00:00Z");
        var r1 = prLeadRow(base, base.plusSeconds(144_000L)); // 40h
        var r2 = prLeadRow(base, base.plusSeconds(36_000L));  // 10h
        var r3 = prLeadRow(base, base.plusSeconds(108_000L)); // 30h
        var r4 = prLeadRow(base, base.plusSeconds(72_000L));  // 20h
        when(pullRequestRepository.findMergedLeadTimesByRepoIdsAndAuthorLogin(any(), any(), any(), any()))
                .thenReturn(List.of(r1, r2, r3, r4));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(PR_LEAD_TIME_HOURS_MEDIAN).getValue()).isEqualTo(25.0);
    }

    @Test
    void leadTimePrs_oddCount_medianIsMiddleElement() {
        Instant base = Instant.parse("2024-01-15T00:00:00Z");
        var r1 = prLeadRow(base, base.plusSeconds(36_000L));  // 10h
        var r2 = prLeadRow(base, base.plusSeconds(72_000L));  // 20h
        var r3 = prLeadRow(base, base.plusSeconds(108_000L)); // 30h
        when(pullRequestRepository.findMergedLeadTimesByRepoIdsAndAuthorLogin(any(), any(), any(), any()))
                .thenReturn(List.of(r1, r2, r3));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(PR_LEAD_TIME_HOURS_MEDIAN).getValue()).isEqualTo(20.0);
    }

    @Test
    void leadTimePrs_emptyData_nothingSaved() {
        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(allSaves()).noneMatch(s -> s.getMetricType() == PR_LEAD_TIME_HOURS_MEDIAN);
    }

    // ─── ISSUE_LEAD_TIME_HOURS_MEDIAN ──────────────────────────────────────────

    @Test
    void leadTimeIssues_threeValues_medianIsMiddle() {
        Instant base = Instant.parse("2024-01-15T00:00:00Z");
        var r1 = issueLeadRow(base, base.plusSeconds(86_400L));   // 24h
        var r2 = issueLeadRow(base, base.plusSeconds(172_800L));  // 48h
        var r3 = issueLeadRow(base, base.plusSeconds(259_200L));  // 72h
        when(issueRepository.findIssueLeadTimesByRepoIds(any(), any(), any()))
                .thenReturn(List.of(r1, r2, r3));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(ISSUE_LEAD_TIME_HOURS_MEDIAN).getValue()).isEqualTo(48.0);
    }

    // ─── PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN ───────────────────────

    @Test
    void leadTimeFirstCommitToMerge_singlePr_hoursFromFirstCommit() {
        Instant commitAt = Instant.parse("2024-01-15T08:00:00Z");
        Instant mergedAt = Instant.parse("2024-01-15T20:00:00Z"); // 12h later

        GitHubPullRequestEntity pr = buildPr(1L, commitAt, mergedAt);
        when(pullRequestRepository.findMergedPrsByRepoIdsAndAuthorLogin(any(), any(), any(), any()))
                .thenReturn(List.of(pr));

        GitCommitEntity commit = new GitCommitEntity();
        commit.setAuthorDate(commitAt);
        when(commitRepository.findCommitsForPr(any(), anyInt())).thenReturn(List.of(commit));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN).getValue()).isEqualTo(12.0);
        verify(pullRequestRepository).saveAll(any());
    }

    @Test
    void leadTimeFirstCommitToMerge_noCommitsForPr_skipped() {
        GitHubPullRequestEntity pr = buildPr(1L, Instant.now(), Instant.now().plusSeconds(3600));
        when(pullRequestRepository.findMergedPrsByRepoIdsAndAuthorLogin(any(), any(), any(), any()))
                .thenReturn(List.of(pr));
        when(commitRepository.findCommitsForPr(any(), anyInt())).thenReturn(List.of());

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(allSaves()).noneMatch(s -> s.getMetricType() == PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN);
    }

    // ─── REVIEW_RESPONSE_TIME_HOURS_MEDIAN ─────────────────────────────────────

    @Test
    void reviewResponseTime_singlePrWithReview_medianHoursSaved() {
        Instant createdAt = Instant.parse("2024-01-15T09:00:00Z");
        Instant reviewAt  = Instant.parse("2024-01-15T21:00:00Z"); // 12h later

        GitHubPullRequestEntity pr = buildPr(1L, createdAt, null);
        when(pullRequestRepository.findMergedPrsByRepoIdsAndAuthorLogin(any(), any(), any(), any()))
                .thenReturn(List.of(pr));

        PrReviewTimestampProjection rev = mock(PrReviewTimestampProjection.class);
        when(rev.getPrId()).thenReturn(1L);
        when(rev.getReviewedAt()).thenReturn(reviewAt);
        when(prReviewRepository.findFirstReviewTimestampsByPrIds(any())).thenReturn(List.of(rev));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(REVIEW_RESPONSE_TIME_HOURS_MEDIAN).getValue()).isEqualTo(12.0);
    }

    @Test
    void reviewResponseTime_noReviewForPr_skipped() {
        GitHubPullRequestEntity pr = buildPr(1L, Instant.now(), null);
        when(pullRequestRepository.findMergedPrsByRepoIdsAndAuthorLogin(any(), any(), any(), any()))
                .thenReturn(List.of(pr));
        // prReviewRepository returns empty (default lenient stub)

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(allSaves()).noneMatch(s -> s.getMetricType() == REVIEW_RESPONSE_TIME_HOURS_MEDIAN);
    }

    // ─── FOCUS_RATIO_DAYS_TASKS ────────────────────────────────────────────────

    @Test
    void focusRatio_weekdayWithCommits_savesOneForThatDay() {
        // FROM = Monday 2024-01-15
        var row = commitsRow(FROM, 2, null);
        when(commitRepository.aggregateCommitsDailyByRepoIdsAndAuthorEmail(any(), any(), any(), any()))
                .thenReturn(List.of(row));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        List<MetricSnapshot> focusSnaps = allSaves().stream()
                .filter(s -> s.getMetricType() == FOCUS_RATIO_DAYS_TASKS)
                .toList();
        assertThat(focusSnaps).hasSize(1);
        assertThat(focusSnaps.get(0).getValue()).isEqualTo(1.0);
        assertThat(focusSnaps.get(0).getDate()).isEqualTo(FROM);
    }

    @Test
    void focusRatio_weekendWithCommits_notSaved() {
        // 2024-01-20 = Saturday, inside [FROM, TO]
        LocalDate saturday = LocalDate.of(2024, 1, 20);
        var row = commitsRow(saturday, 1, null);
        when(commitRepository.aggregateCommitsDailyByRepoIdsAndAuthorEmail(any(), any(), any(), any()))
                .thenReturn(List.of(row));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(allSaves()).noneMatch(s -> s.getMetricType() == FOCUS_RATIO_DAYS_TASKS);
    }

    // ─── AFTER_HOURS_COMMIT_RATIO ──────────────────────────────────────────────

    @Test
    void afterHoursRatio_commitAtEveningBerlinTime_ratioOne() {
        // 19:00 UTC = 20:00 Berlin (UTC+1 in January) — outside 09–18 → after hours
        Instant eveningUtc = Instant.parse("2024-01-15T19:00:00Z");
        var row = commitDetailRow(eveningUtc, 10, 5, StatsStatus.COMPLETE);
        when(commitRepository.findCommitDetailsByRepoIdsAndAuthorEmail(any(), any(), any(), any()))
                .thenReturn(List.of(row));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(AFTER_HOURS_COMMIT_RATIO).getValue()).isEqualTo(1.0);
    }

    @Test
    void afterHoursRatio_commitAtMidMorningBerlinTime_ratioZero() {
        // 09:00 UTC = 10:00 Berlin — inside 09–18 on a weekday → in hours
        Instant morningUtc = Instant.parse("2024-01-15T09:00:00Z");
        var row = commitDetailRow(morningUtc, 10, 5, StatsStatus.COMPLETE);
        when(commitRepository.findCommitDetailsByRepoIdsAndAuthorEmail(any(), any(), any(), any()))
                .thenReturn(List.of(row));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(AFTER_HOURS_COMMIT_RATIO).getValue()).isEqualTo(0.0);
    }

    @Test
    void afterHoursRatio_weekendCommit_countsAsAfterHours() {
        // 2024-01-20 09:00 UTC = Saturday 10:00 Berlin → weekend → after hours
        Instant saturdayMorning = Instant.parse("2024-01-20T09:00:00Z");
        var row = commitDetailRow(saturdayMorning, 5, 0, StatsStatus.COMPLETE);
        when(commitRepository.findCommitDetailsByRepoIdsAndAuthorEmail(any(), any(), any(), any()))
                .thenReturn(List.of(row));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(AFTER_HOURS_COMMIT_RATIO).getValue()).isEqualTo(1.0);
    }

    // ─── REFACTOR_RATIO ────────────────────────────────────────────────────────

    @Test
    void refactorRatio_deletionsExceedAdditions_ratioOne() {
        // 1 enriched commit, deletions(50) > additions(10) → ratio = 1.0
        Instant ts = Instant.parse("2024-01-15T10:00:00Z");
        var row = commitDetailRow(ts, 10, 50, StatsStatus.COMPLETE);
        when(commitRepository.findCommitDetailsByRepoIdsAndAuthorEmail(any(), any(), any(), any()))
                .thenReturn(List.of(row));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(REFACTOR_RATIO).getValue()).isEqualTo(1.0);
    }

    @Test
    void refactorRatio_pendingStats_notSaved() {
        // PENDING commits have placeholder 0/0 diff — should not skew the ratio
        Instant ts = Instant.parse("2024-01-15T10:00:00Z");
        var row = commitDetailRow(ts, 0, 0, StatsStatus.PENDING);
        when(commitRepository.findCommitDetailsByRepoIdsAndAuthorEmail(any(), any(), any(), any()))
                .thenReturn(List.of(row));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(allSaves()).noneMatch(s -> s.getMetricType() == REFACTOR_RATIO);
    }

    // ─── DEEP_WORK_STREAK_DAYS ─────────────────────────────────────────────────

    @Test
    void deepWorkStreak_threeConsecutiveDays_streakThree() {
        var d1 = commitsRow(FROM, 1, null);
        var d2 = commitsRow(FROM.plusDays(1), 1, null);
        var d3 = commitsRow(FROM.plusDays(2), 1, null);
        when(commitRepository.aggregateCommitsDailyByRepoIdsAndAuthorEmail(any(), any(), any(), any()))
                .thenReturn(List.of(d1, d2, d3));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(DEEP_WORK_STREAK_DAYS).getValue()).isEqualTo(3.0);
    }

    @Test
    void deepWorkStreak_gapBreaksStreak_longestRunReturned() {
        // Jan15 (streak 1), gap, Jan17+Jan18 (streak 2) → max = 2
        var d1 = commitsRow(FROM, 1, null);
        var d2 = commitsRow(FROM.plusDays(2), 1, null);
        var d3 = commitsRow(FROM.plusDays(3), 1, null);
        when(commitRepository.aggregateCommitsDailyByRepoIdsAndAuthorEmail(any(), any(), any(), any()))
                .thenReturn(List.of(d1, d2, d3));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(DEEP_WORK_STREAK_DAYS).getValue()).isEqualTo(2.0);
    }

    @Test
    void deepWorkStreak_singleDay_streakOne() {
        var row = commitsRow(FROM, 5, null);
        when(commitRepository.aggregateCommitsDailyByRepoIdsAndAuthorEmail(any(), any(), any(), any()))
                .thenReturn(List.of(row));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(DEEP_WORK_STREAK_DAYS).getValue()).isEqualTo(1.0);
    }

    // ─── MERGE_TO_MAIN_FREQUENCY_PER_WEEK ──────────────────────────────────────

    @Test
    void mergeToMainFrequency_twoWeeks_averagePerWeek() {
        // Week 3 (Jan 15+16): 2+1=3 commits; Week 4 (Jan 22): 5 commits → avg = 4.0
        LocalDate week4Day = LocalDate.of(2024, 1, 22);
        var d1 = commitsRow(FROM, 2, null);
        var d2 = commitsRow(FROM.plusDays(1), 1, null);
        var d3 = commitsRow(week4Day, 5, null);
        when(commitRepository.aggregateCommitsDailyByRepoIdsAndAuthorEmail(any(), any(), any(), any()))
                .thenReturn(List.of(d1, d2, d3));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(MERGE_TO_MAIN_FREQUENCY_PER_WEEK).getValue()).isEqualTo(4.0);
    }

    @Test
    void mergeToMainFrequency_zeroCommitDaysIgnored_onlyNonZeroWeeksCount() {
        // Row with count=0 → skipped when building the week map → nothing saved
        var d1 = commitsRow(FROM, 0, null);
        when(commitRepository.aggregateCommitsDailyByRepoIdsAndAuthorEmail(any(), any(), any(), any()))
                .thenReturn(List.of(d1));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(allSaves()).noneMatch(s -> s.getMetricType() == MERGE_TO_MAIN_FREQUENCY_PER_WEEK);
    }

    // ─── KNOWLEDGE_SILO_SCORE ──────────────────────────────────────────────────

    @Test
    void knowledgeSilo_userOwns90PercentOfCommits_scorePoint9() {
        var total = repoCountRow(REPO_ID, 10);
        var user  = repoCountRow(REPO_ID, 9);
        when(commitRepository.countTotalCommitsByRepoIds(any(), any(), any())).thenReturn(List.of(total));
        when(commitRepository.countCommitsByRepoIdsAndAuthorEmail(any(), any(), any(), any())).thenReturn(List.of(user));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(KNOWLEDGE_SILO_SCORE).getValue()).isEqualTo(0.9);
    }

    @Test
    void knowledgeSilo_userHasNoCommitsInRepo_scoreZero() {
        var total = repoCountRow(REPO_ID, 10);
        when(commitRepository.countTotalCommitsByRepoIds(any(), any(), any())).thenReturn(List.of(total));
        // user count returns empty → 0 commits by user
        when(commitRepository.countCommitsByRepoIdsAndAuthorEmail(any(), any(), any(), any()))
                .thenReturn(List.of());

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(KNOWLEDGE_SILO_SCORE).getValue()).isEqualTo(0.0);
    }

    // ─── PR_SIZE_COMPLEXITY_SCORE ──────────────────────────────────────────────

    @Test
    void prSizeComplexity_twoPrs_medianComplexityComputed() {
        // PR1: (100+50)/2 = 75.0; PR2: (200+100)/3 = 100.0; median([75,100]) = 87.5
        GitHubPullRequestEntity pr1 = buildPr(1L, null, null);
        pr1.setAdditions(100); pr1.setDeletions(50); pr1.setCommitsCount(2);

        GitHubPullRequestEntity pr2 = buildPr(2L, null, null);
        pr2.setAdditions(200); pr2.setDeletions(100); pr2.setCommitsCount(3);

        when(pullRequestRepository.findMergedPrsByRepoIdsAndAuthorLogin(any(), any(), any(), any()))
                .thenReturn(List.of(pr1, pr2));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(PR_SIZE_COMPLEXITY_SCORE).getValue()).isEqualTo(87.5);
    }

    @Test
    void prSizeComplexity_zeroCommitsCount_treatedAsOne() {
        // commitsCount=0 → denominator forced to 1 to avoid division by zero
        GitHubPullRequestEntity pr = buildPr(1L, null, null);
        pr.setAdditions(100); pr.setDeletions(50); pr.setCommitsCount(0);

        when(pullRequestRepository.findMergedPrsByRepoIdsAndAuthorLogin(any(), any(), any(), any()))
                .thenReturn(List.of(pr));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(PR_SIZE_COMPLEXITY_SCORE).getValue()).isEqualTo(150.0);
    }

    // ─── MERGE_WITHOUT_REVIEW_RATIO ────────────────────────────────────────────

    @Test
    void mergeWithoutReview_halfPrsUnreviewed_ratioHalf() {
        GitHubPullRequestEntity prReviewed    = buildPr(1L, null, null);
        GitHubPullRequestEntity prUnreviewed  = buildPr(2L, null, null);

        when(pullRequestRepository.findMergedPrsByRepoIdsAndAuthorLogin(any(), any(), any(), any()))
                .thenReturn(List.of(prReviewed, prUnreviewed));

        PrReviewTimestampProjection rev = mock(PrReviewTimestampProjection.class);
        when(rev.getPrId()).thenReturn(1L);
        when(rev.getReviewedAt()).thenReturn(Instant.now());
        when(prReviewRepository.findFirstReviewTimestampsByPrIds(any())).thenReturn(List.of(rev));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(MERGE_WITHOUT_REVIEW_RATIO).getValue()).isEqualTo(0.5);
    }

    @Test
    void mergeWithoutReview_allPrsReviewed_ratioZero() {
        GitHubPullRequestEntity pr = buildPr(1L, null, null);
        when(pullRequestRepository.findMergedPrsByRepoIdsAndAuthorLogin(any(), any(), any(), any()))
                .thenReturn(List.of(pr));

        PrReviewTimestampProjection rev = mock(PrReviewTimestampProjection.class);
        when(rev.getPrId()).thenReturn(1L);
        when(rev.getReviewedAt()).thenReturn(Instant.now());
        when(prReviewRepository.findFirstReviewTimestampsByPrIds(any())).thenReturn(List.of(rev));

        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(savedOf(MERGE_WITHOUT_REVIEW_RATIO).getValue()).isEqualTo(0.0);
    }

    @Test
    void mergeWithoutReview_noPrs_nothingSaved() {
        service.calculateDailyMetrics(USER_ID, FROM, TO);

        assertThat(allSaves()).noneMatch(s -> s.getMetricType() == MERGE_WITHOUT_REVIEW_RATIO);
    }

    // ─── saveMetricSnapshot (QF-6) ────────────────────────────────────────────

    @Test
    void saveMetricSnapshot_newSnapshot_savesNewRow() {
        when(snapshotRepository.findExisting(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        service.saveMetricSnapshot(user, null, FROM, DAILY_COMMITS_COUNT, 7.0, null, null, null);

        verify(snapshotRepository).save(argThat(s ->
                s.getValue() == 7.0 && s.getMetricType() == DAILY_COMMITS_COUNT && s.getUser() == user
        ));
    }

    @Test
    void saveMetricSnapshot_existingSnapshot_updatesValueWithoutDuplicate() {
        MetricSnapshot existing = new MetricSnapshot();
        existing.setId(99L);
        existing.setValue(3.0);
        when(snapshotRepository.findExisting(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(existing));

        service.saveMetricSnapshot(user, null, FROM, DAILY_COMMITS_COUNT, 7.0, null, null, null);

        verify(snapshotRepository, times(1)).save(argThat(s ->
                s.getId() == 99L && s.getValue() == 7.0
        ));
    }
}
