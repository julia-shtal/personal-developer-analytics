package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPrReviewRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.metrics.model.*;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Verifies that the T4.4 projection refactoring produces identical numeric values
 * to the previous Object[] implementation. Each test exercises one projection type
 * end-to-end through MetricsService into a captured MetricSnapshot.
 */
@ExtendWith(MockitoExtension.class)
class MetricsProjectionTest {

    @Mock MetricSnapshotRepository snapshotRepository;
    @Mock GitCommitEntityRepository commitRepository;
    @Mock GitHubPullRequestRepository pullRequestRepository;
    @Mock GitHubPrReviewRepository prReviewRepository;
    @Mock IssueRepository issueRepository;
    @Mock UserRepository userRepository;
    @Mock TeamRepository teamRepository;
    @Mock GitRepositoryEntityRepository gitRepoRepository;
    @Mock UserRepoRegistrationRepository userRepoRegRepository;

    MetricsService service;

    static final Long USER_ID = 1L;
    static final Long REPO_ID = 10L;
    static final LocalDate DATE = LocalDate.of(2024, 1, 15);

    User user;
    GitRepositoryEntity repo;

    @BeforeEach
    void setUp() {
        service = new MetricsService(
                snapshotRepository, commitRepository, pullRequestRepository,
                prReviewRepository, issueRepository, userRepository,
                teamRepository, gitRepoRepository, userRepoRegRepository);

        user = new User();
        user.setId(USER_ID);
        user.setEmail("dev@example.com");
        user.setTimezone("UTC");
        // githubLogin intentionally null → PR / review metrics skipped

        repo = new GitRepositoryEntity();
        repo.setId(REPO_ID);

        lenient().when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        lenient().when(userRepoRegRepository.findRepoIdsByUserId(USER_ID)).thenReturn(List.of(REPO_ID));
        lenient().when(teamRepository.findByMembersId(USER_ID)).thenReturn(List.of());
        lenient().when(gitRepoRepository.getReferenceById(REPO_ID)).thenReturn(repo);
        lenient().when(snapshotRepository.findExisting(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Default empty returns so methods that aren't under test return quickly
        lenient().when(commitRepository.aggregateCommitsDailyByRepoIdsAndAuthorEmail(anyList(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(commitRepository.aggregateChurnDailyByRepoIdsAndAuthorEmail(anyList(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(commitRepository.findCommitDetailsByRepoIdsAndAuthorEmail(anyList(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(commitRepository.countTotalCommitsByRepoIds(anyList(), any(), any()))
                .thenReturn(List.of());
        lenient().when(commitRepository.countCommitsByRepoIdsAndAuthorEmail(anyList(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(issueRepository.aggregateIssuesCreatedDailyByRepoIds(anyList(), any(), any()))
                .thenReturn(List.of());
        lenient().when(issueRepository.aggregateIssuesClosedDailyByRepoIds(anyList(), any(), any()))
                .thenReturn(List.of());
        lenient().when(issueRepository.findIssueLeadTimesByRepoIds(anyList(), any(), any()))
                .thenReturn(List.of());
    }

    // ── DailyCommitsProjection ────────────────────────────────────────────────

    @Test
    void dailyCommitsProjection_countsAndAvgSizeSavedCorrectly() {
        DailyCommitsProjection row = mock(DailyCommitsProjection.class);
        when(row.getDay()).thenReturn(Date.valueOf(DATE));
        when(row.getRepoId()).thenReturn(REPO_ID);
        when(row.getCommitsCount()).thenReturn(7L);
        when(row.getAvgSize()).thenReturn(42.0);

        when(commitRepository.aggregateCommitsDailyByRepoIdsAndAuthorEmail(anyList(), any(), any(), any()))
                .thenReturn(List.of(row));

        service.calculateDailyMetrics(USER_ID, DATE, DATE);

        ArgumentCaptor<MetricSnapshot> cap = ArgumentCaptor.forClass(MetricSnapshot.class);
        verify(snapshotRepository, atLeast(2)).save(cap.capture());

        MetricSnapshot commits = cap.getAllValues().stream()
                .filter(s -> s.getMetricType() == MetricType.DAILY_COMMITS_COUNT).findFirst().orElseThrow();
        MetricSnapshot avg = cap.getAllValues().stream()
                .filter(s -> s.getMetricType() == MetricType.DAILY_COMMITS_AVG_SIZE).findFirst().orElseThrow();

        assertThat(commits.getValue()).isEqualTo(7.0);
        assertThat(avg.getValue()).isEqualTo(42.0);
    }

    @Test
    void dailyCommitsProjection_nullAvgSize_savedAsZero() {
        DailyCommitsProjection row = mock(DailyCommitsProjection.class);
        when(row.getDay()).thenReturn(Date.valueOf(DATE));
        when(row.getRepoId()).thenReturn(REPO_ID);
        when(row.getCommitsCount()).thenReturn(3L);
        when(row.getAvgSize()).thenReturn(null);

        when(commitRepository.aggregateCommitsDailyByRepoIdsAndAuthorEmail(anyList(), any(), any(), any()))
                .thenReturn(List.of(row));

        service.calculateDailyMetrics(USER_ID, DATE, DATE);

        ArgumentCaptor<MetricSnapshot> cap = ArgumentCaptor.forClass(MetricSnapshot.class);
        verify(snapshotRepository, atLeast(2)).save(cap.capture());

        MetricSnapshot avg = cap.getAllValues().stream()
                .filter(s -> s.getMetricType() == MetricType.DAILY_COMMITS_AVG_SIZE).findFirst().orElseThrow();
        assertThat(avg.getValue()).isEqualTo(0.0);
    }

    // ── DailyChurnProjection ──────────────────────────────────────────────────

    @Test
    void dailyChurnProjection_ratioCalculatedCorrectly() {
        DailyChurnProjection row = mock(DailyChurnProjection.class);
        when(row.getDay()).thenReturn(Date.valueOf(DATE));
        when(row.getRepoId()).thenReturn(REPO_ID);
        when(row.getAdditions()).thenReturn(30L);
        when(row.getDeletions()).thenReturn(10L);   // churn = 10 / (30+10) = 0.25

        when(commitRepository.aggregateChurnDailyByRepoIdsAndAuthorEmail(anyList(), any(), any(), any()))
                .thenReturn(List.of(row));

        service.calculateDailyMetrics(USER_ID, DATE, DATE);

        ArgumentCaptor<MetricSnapshot> cap = ArgumentCaptor.forClass(MetricSnapshot.class);
        verify(snapshotRepository, atLeast(1)).save(cap.capture());

        MetricSnapshot churn = cap.getAllValues().stream()
                .filter(s -> s.getMetricType() == MetricType.DAILY_CHURN_RATIO).findFirst().orElseThrow();
        assertThat(churn.getValue()).isEqualTo(0.25);
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
        when(commitRepository.countCommitsByRepoIdsAndAuthorEmail(anyList(), any(), any(), any())).thenReturn(List.of(mine));

        service.calculateDailyMetrics(USER_ID, DATE, DATE);

        ArgumentCaptor<MetricSnapshot> cap = ArgumentCaptor.forClass(MetricSnapshot.class);
        verify(snapshotRepository, atLeast(1)).save(cap.capture());

        MetricSnapshot silo = cap.getAllValues().stream()
                .filter(s -> s.getMetricType() == MetricType.KNOWLEDGE_SILO_SCORE).findFirst().orElseThrow();
        assertThat(silo.getValue()).isEqualTo(0.6);
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

        when(commitRepository.findCommitDetailsByRepoIdsAndAuthorEmail(anyList(), any(), any(), any()))
                .thenReturn(List.of(row));

        service.calculateDailyMetrics(USER_ID, DATE, DATE);

        ArgumentCaptor<MetricSnapshot> cap = ArgumentCaptor.forClass(MetricSnapshot.class);
        verify(snapshotRepository, atLeast(1)).save(cap.capture());

        MetricSnapshot afterHours = cap.getAllValues().stream()
                .filter(s -> s.getMetricType() == MetricType.AFTER_HOURS_COMMIT_RATIO).findFirst().orElseThrow();
        assertThat(afterHours.getValue()).isEqualTo(1.0);  // 1 out-of-hours / 1 total
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

        when(commitRepository.findCommitDetailsByRepoIdsAndAuthorEmail(anyList(), any(), any(), any()))
                .thenReturn(List.of(row));

        service.calculateDailyMetrics(USER_ID, DATE, DATE);

        ArgumentCaptor<MetricSnapshot> cap = ArgumentCaptor.forClass(MetricSnapshot.class);
        verify(snapshotRepository, atLeast(1)).save(cap.capture());

        MetricSnapshot refactor = cap.getAllValues().stream()
                .filter(s -> s.getMetricType() == MetricType.REFACTOR_RATIO).findFirst().orElseThrow();
        assertThat(refactor.getValue()).isEqualTo(1.0);  // 1 refactor / 1 enriched
    }
}
