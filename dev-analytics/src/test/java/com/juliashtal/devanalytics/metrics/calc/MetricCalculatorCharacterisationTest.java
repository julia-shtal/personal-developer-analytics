package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPrReviewRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.metrics.MetricSnapshotRepository;
import com.juliashtal.devanalytics.metrics.model.MetricType;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Proves that the registry path covers every MetricType value and that all
 * calculators handle empty repository results without throwing. Exact numeric
 * values are covered by per-calculator unit tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetricCalculatorCharacterisationTest {

    @Mock MetricSnapshotRepository snapshotRepository;
    @Mock GitCommitEntityRepository commitRepository;
    @Mock GitHubPullRequestRepository pullRequestRepository;
    @Mock GitHubPrReviewRepository prReviewRepository;
    @Mock IssueRepository issueRepository;
    @Mock GitRepositoryEntityRepository gitRepoRepository;

    static final Long REPO_ID = 10L;
    static final LocalDate FROM = LocalDate.of(2024, 1, 15);
    static final LocalDate TO   = LocalDate.of(2024, 1, 21);

    GitRepositoryEntity repo;

    @BeforeEach
    void setUp() {
        repo = new GitRepositoryEntity();
        repo.setId(REPO_ID);

        when(gitRepoRepository.getReferenceById(REPO_ID)).thenReturn(repo);
        when(snapshotRepository.findExisting(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(commitRepository.aggregateCommitsDailyByRepoIdsAndIdentity(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(pullRequestRepository.aggregatePrCreatedDailyByRepoIdsAndAuthorGithubId(any(), any(), any(), any())).thenReturn(List.of());
        when(pullRequestRepository.aggregatePrMergedDailyByRepoIdsAndAuthorGithubId(any(), any(), any(), any())).thenReturn(List.of());
        when(issueRepository.aggregateIssuesCreatedDailyByRepoIdsAndIdentity(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(issueRepository.aggregateIssuesClosedDailyByRepoIdsAndIdentity(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(commitRepository.aggregateChurnDailyByRepoIdsAndIdentity(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(pullRequestRepository.findMergedLeadTimesByRepoIdsAndAuthorGithubId(any(), any(), any(), any())).thenReturn(List.of());
        when(issueRepository.findIssueLeadTimesByRepoIdsAndIdentity(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(pullRequestRepository.findMergedPrsByRepoIdsAndAuthorGithubId(any(), any(), any(), any())).thenReturn(List.of());
        when(commitRepository.findCommitDetailsByRepoIdsAndIdentity(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(commitRepository.countTotalCommitsByRepoIds(any(), any(), any())).thenReturn(List.of());
        when(commitRepository.countCommitsByRepoIdsAndIdentity(any(), any(), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void registry_coversEveryMetricType() {
        MetricCalculatorRegistry registry = buildRegistry();
        Set<MetricType> covered = registry.all().stream()
                .flatMap(c -> c.produces().stream())
                .collect(Collectors.toSet());

        assertThat(covered).containsExactlyInAnyOrder(MetricType.values());
    }

    @Test
    void registry_calculateAll_completesWithoutErrorOnEmptyData() {
        User user = new User();
        user.setId(1L);
        user.setEmail("dev@example.com");
        user.setGithubLogin("devuser");
        user.setGithubUserId(101L);
        user.setTimezone("UTC");

        Instant from = FROM.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to   = TO.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        // Fully populated so every calculator gets past its identity guard and actually runs;
        // the point of this test is that none of them throws on empty data.
        AuthorIdentity identity = new AuthorIdentity(Set.of("dev@example.com"), 101L, "jira-acct-1");
        MetricCalcContext ctx = new MetricCalcContext(user, null, List.of(REPO_ID), identity, from, to, FROM, TO);

        MetricCalculatorRegistry registry = buildRegistry();
        assertThatCode(() -> registry.all().forEach(c -> c.calculate(ctx))).doesNotThrowAnyException();
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
}
