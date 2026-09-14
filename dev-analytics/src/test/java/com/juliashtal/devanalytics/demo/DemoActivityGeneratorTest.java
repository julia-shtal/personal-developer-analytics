package com.juliashtal.devanalytics.demo;

import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.StatsSkipReason;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two properties the demo dataset is worth having: it is reproducible from its seed, and
 * every commit carries an identifier the attribution rules can match, so the pipeline it feeds
 * produces figures rather than silence.
 */
class DemoActivityGeneratorTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 5);
    private static final String    EMAIL = "demo@demo.com";
    private static final long      GH_ID = 900_000_001L;

    private List<GitCommitEntity> commits(int seed) {
        return DemoActivityGenerator.buildCommits(EMAIL, "demo-developer", GH_ID, START, 12, seed);
    }

    @Test
    void buildCommits_sameSeed_producesTheIdenticalDataset() {
        List<GitCommitEntity> first  = commits(42);
        List<GitCommitEntity> second = commits(42);

        assertThat(first).hasSameSizeAs(second);
        assertThat(first).extracting(GitCommitEntity::getHash)
                .isEqualTo(second.stream().map(GitCommitEntity::getHash).toList());
        assertThat(first).extracting(GitCommitEntity::getAdditions)
                .isEqualTo(second.stream().map(GitCommitEntity::getAdditions).toList());
    }

    @Test
    void buildCommits_differentSeed_producesADifferentDataset() {
        assertThat(commits(42)).extracting(GitCommitEntity::getHash)
                .isNotEqualTo(commits(43).stream().map(GitCommitEntity::getHash).toList());
    }

    @Test
    void buildCommits_everyCommit_carriesTheDeclaredAddressAndAccountId() {
        assertThat(commits(42)).allSatisfy(c -> {
            assertThat(c.getAuthorEmail()).isEqualTo(EMAIL);
            assertThat(c.getAuthorGithubId()).isEqualTo(GH_ID);
        });
    }

    @Test
    void buildCommits_everyCommit_fallsInsideTheRequestedSpan() {
        LocalDate end = START.plusWeeks(12);

        assertThat(commits(42)).allSatisfy(c -> {
            LocalDate day = c.getAuthorDate().atZone(java.time.ZoneOffset.UTC).toLocalDate();
            assertThat(day).isBetween(START, end);
        });
    }

    @Test
    void buildCommits_someCommits_areSkippedSoCoverageHasSomethingToReport() {
        List<GitCommitEntity> all = commits(42);

        assertThat(all).anySatisfy(c -> assertThat(c.getStatsStatus()).isEqualTo(StatsStatus.SKIPPED));
        assertThat(all).filteredOn(c -> c.getStatsStatus() == StatsStatus.SKIPPED)
                .allSatisfy(c -> assertThat(c.getStatsSkipReason()).isNotNull())
                .hasSizeLessThan(all.size() / 5);
    }

    @Test
    void buildCommits_completeCommits_carryNoSkipReason() {
        assertThat(commits(42)).filteredOn(c -> c.getStatsStatus() == StatsStatus.COMPLETE)
                .allSatisfy(c -> assertThat(c.getStatsSkipReason()).isNull())
                .isNotEmpty();
    }

    @Test
    void buildCommits_hashes_areUnique() {
        List<String> hashes = commits(42).stream().map(GitCommitEntity::getHash).toList();

        assertThat(hashes).doesNotHaveDuplicates();
    }

    @Test
    void buildCommits_skipReasons_coverBothLiveCauses() {
        assertThat(commits(42)).filteredOn(c -> c.getStatsStatus() == StatsStatus.SKIPPED)
                .extracting(GitCommitEntity::getStatsSkipReason)
                .contains(StatsSkipReason.DIFF_TOO_LARGE, StatsSkipReason.RECORD_UNAVAILABLE);
    }

    @Test
    void buildPullRequests_sameSeed_producesTheIdenticalDataset() {
        var first  = DemoActivityGenerator.buildPullRequests(GH_ID, "demo-developer", START, 12, 42);
        var second = DemoActivityGenerator.buildPullRequests(GH_ID, "demo-developer", START, 12, 42);

        assertThat(first).extracting(com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity::getNumber)
                .isEqualTo(second.stream().map(
                        com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity::getNumber).toList());
    }

    @Test
    void buildPullRequests_everyPr_carriesTheAuthorAccountId() {
        assertThat(DemoActivityGenerator.buildPullRequests(GH_ID, "demo-developer", START, 12, 42))
                .isNotEmpty()
                .allSatisfy(p -> assertThat(p.getAuthorGithubId()).isEqualTo(GH_ID));
    }

    @Test
    void buildPullRequests_theSet_containsMergedAndStillOpenPrs() {
        var prs = DemoActivityGenerator.buildPullRequests(GH_ID, "demo-developer", START, 12, 42);

        assertThat(prs).anySatisfy(p -> assertThat(p.isMerged()).isTrue());
        assertThat(prs).anySatisfy(p -> {
            assertThat(p.getMergedAt()).isNull();
            assertThat(p.getClosedAt()).isNull();
        });
    }

    @Test
    void buildPullRequests_mergedPrs_closeAfterTheyOpen() {
        assertThat(DemoActivityGenerator.buildPullRequests(GH_ID, "demo-developer", START, 12, 42))
                .filteredOn(com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity::isMerged)
                .isNotEmpty()
                .allSatisfy(p -> assertThat(p.getMergedAt()).isAfter(p.getCreatedAt()));
    }

    @Test
    void buildReviews_reviewerIsTheOtherUser_soParticipationIsNotSelfReview() {
        var prs = DemoActivityGenerator.buildPullRequests(GH_ID, "demo-developer", START, 12, 42);
        var reviews = DemoActivityGenerator.buildReviews(prs, 900_000_002L, "demo-teammate", 42);

        assertThat(reviews).isNotEmpty()
                .allSatisfy(r -> assertThat(r.getReviewerGithubId()).isEqualTo(900_000_002L));
    }

    @Test
    void buildReviews_someMergedPrs_haveNoReviewSoMergeWithoutReviewIsNonZero() {
        var prs = DemoActivityGenerator.buildPullRequests(GH_ID, "demo-developer", START, 12, 42);
        var reviews = DemoActivityGenerator.buildReviews(prs, 900_000_002L, "demo-teammate", 42);
        var reviewed = reviews.stream().map(r -> r.getPullRequest().getNumber()).toList();

        assertThat(prs).filteredOn(com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity::isMerged)
                .anySatisfy(p -> assertThat(reviewed).doesNotContain(p.getNumber()));
    }

    @Test
    void buildReviews_everyReview_lands0to48hAfterItsPrOpened() {
        var prs = DemoActivityGenerator.buildPullRequests(GH_ID, "demo-developer", START, 12, 42);

        assertThat(DemoActivityGenerator.buildReviews(prs, 900_000_002L, "demo-teammate", 42))
                .allSatisfy(r -> {
                    var opened = r.getPullRequest().getCreatedAt();
                    assertThat(r.getSubmittedAt()).isAfterOrEqualTo(opened);
                    assertThat(r.getSubmittedAt()).isBeforeOrEqualTo(opened.plusSeconds(48 * 3600));
                });
    }

    @Test
    void buildIssues_sameSeed_producesTheIdenticalDataset() {
        var first  = DemoActivityGenerator.buildIssues(GH_ID, "demo-developer", START, 12, 42);
        var second = DemoActivityGenerator.buildIssues(GH_ID, "demo-developer", START, 12, 42);

        assertThat(first).extracting(
                        com.juliashtal.devanalytics.issue.model.IssueEntity::getSourceIssueKey)
                .isEqualTo(second.stream().map(
                        com.juliashtal.devanalytics.issue.model.IssueEntity::getSourceIssueKey).toList());
    }

    @Test
    void buildIssues_everyIssue_carriesTheCreatorAndAssigneeAccountId() {
        assertThat(DemoActivityGenerator.buildIssues(GH_ID, "demo-developer", START, 12, 42))
                .isNotEmpty()
                .allSatisfy(i -> {
                    assertThat(i.getCreatorGithubId()).isEqualTo(GH_ID);
                    assertThat(i.getAssigneeGithubId()).isEqualTo(GH_ID);
                    assertThat(i.getSource())
                            .isEqualTo(com.juliashtal.devanalytics.issue.model.IssueSource.GITHUB);
                });
    }

    @Test
    void buildIssues_theSet_containsClosedAndStillOpenIssues() {
        var issues = DemoActivityGenerator.buildIssues(GH_ID, "demo-developer", START, 12, 42);

        assertThat(issues).anySatisfy(i -> assertThat(i.getClosedAt()).isNotNull());
        assertThat(issues).anySatisfy(i -> assertThat(i.getClosedAt()).isNull());
    }

    @Test
    void buildIssues_closedIssues_closeAfterTheyOpen() {
        assertThat(DemoActivityGenerator.buildIssues(GH_ID, "demo-developer", START, 12, 42))
                .filteredOn(i -> i.getClosedAt() != null)
                .isNotEmpty()
                .allSatisfy(i -> assertThat(i.getClosedAt()).isAfter(i.getCreatedAt()));
    }

    @Test
    void buildIssues_keys_areUnique() {
        var keys = DemoActivityGenerator.buildIssues(GH_ID, "demo-developer", START, 12, 42).stream()
                .map(com.juliashtal.devanalytics.issue.model.IssueEntity::getSourceIssueKey).toList();

        assertThat(keys).doesNotHaveDuplicates();
    }

    @Test
    void linkCommitsToPullRequests_everyMergedPr_claimsAnEarlierCommit() {
        var commits = commits(42);
        var prs     = DemoActivityGenerator.buildPullRequests(GH_ID, "demo-developer", START, 12, 42);

        DemoActivityGenerator.linkCommitsToPullRequests(commits, prs);

        assertThat(prs).filteredOn(com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity::isMerged)
                .isNotEmpty()
                .allSatisfy(pr -> assertThat(commits)
                        .anySatisfy(c -> {
                            assertThat(c.getMessage()).contains("(#" + pr.getNumber() + ")");
                            assertThat(c.getAuthorDate()).isBefore(pr.getMergedAt());
                        }));
    }

    @Test
    void linkCommitsToPullRequests_noCommit_isClaimedByTwoPullRequests() {
        var commits = commits(42);
        var prs     = DemoActivityGenerator.buildPullRequests(GH_ID, "demo-developer", START, 12, 42);

        DemoActivityGenerator.linkCommitsToPullRequests(commits, prs);

        assertThat(commits).allSatisfy(c ->
                assertThat(c.getMessage().chars().filter(ch -> ch == '#').count()).isLessThanOrEqualTo(1));
    }

    @Test
    void linkCommitsToPullRequests_openPrs_claimNoCommit() {
        var commits = commits(42);
        var prs     = DemoActivityGenerator.buildPullRequests(GH_ID, "demo-developer", START, 12, 42);

        DemoActivityGenerator.linkCommitsToPullRequests(commits, prs);

        assertThat(prs).filteredOn(p -> !p.isMerged())
                .isNotEmpty()
                .allSatisfy(pr -> assertThat(commits)
                        .noneSatisfy(c -> assertThat(c.getMessage()).contains("(#" + pr.getNumber() + ")")));
    }

    @Test
    void linkCommitsToPullRequests_sameInputs_produceTheIdenticalTagging() {
        var firstCommits = commits(42);
        DemoActivityGenerator.linkCommitsToPullRequests(firstCommits,
                DemoActivityGenerator.buildPullRequests(GH_ID, "demo-developer", START, 12, 42));
        var secondCommits = commits(42);
        DemoActivityGenerator.linkCommitsToPullRequests(secondCommits,
                DemoActivityGenerator.buildPullRequests(GH_ID, "demo-developer", START, 12, 42));

        assertThat(firstCommits).extracting(GitCommitEntity::getMessage)
                .isEqualTo(secondCommits.stream().map(GitCommitEntity::getMessage).toList());
    }
}
