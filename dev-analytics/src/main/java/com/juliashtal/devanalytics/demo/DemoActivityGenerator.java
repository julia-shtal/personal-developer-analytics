package com.juliashtal.devanalytics.demo;

import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.StatsSkipReason;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.github.model.GitHubPrReviewEntity;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.issue.model.IssueEntity;
import com.juliashtal.devanalytics.issue.model.IssueSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Builds the deterministic demo activity set the metric pipeline then computes from.
 *
 * <p>Every record carries the attribution identifiers the calculators match on, because a record
 * without them is invisible to the pipeline and would make the demo look like a calculator bug.</p>
 */
public final class DemoActivityGenerator {

    /** One commit in ~14 is skipped, enough for the coverage endpoint to report without skewing figures. */
    private static final int SKIP_EVERY = 14;

    private DemoActivityGenerator() {
    }

    /**
     * Commits spread over {@code weeks} whole weeks from {@code start}, 0–5 per weekday and
     * 0–1 per weekend day.
     */
    public static List<GitCommitEntity> buildCommits(String authorEmail, String authorName,
                                                     long authorGithubId, LocalDate start,
                                                     int weeks, int seed) {
        Random random = new Random(seed);
        List<GitCommitEntity> commits = new ArrayList<>();
        int sequence = 0;

        for (int day = 0; day < weeks * 7; day++) {
            LocalDate date = start.plusDays(day);
            boolean weekend = date.getDayOfWeek().getValue() >= 6;
            int count = weekend ? random.nextInt(2) : random.nextInt(6);

            for (int i = 0; i < count; i++) {
                // 08:00–21:59 UTC: the after-hours metric needs both sides of the 18:00 boundary.
                Instant at = date.atStartOfDay(ZoneOffset.UTC)
                        .plusHours(8 + random.nextInt(14))
                        .plusMinutes(random.nextInt(60))
                        .toInstant();
                commits.add(commit(authorEmail, authorName, authorGithubId, at, sequence++, random));
            }
        }
        return commits;
    }

    private static GitCommitEntity commit(String authorEmail, String authorName, long authorGithubId,
                                          Instant at, int sequence, Random random) {
        GitCommitEntity c = new GitCommitEntity();
        c.setHash(String.format("%040x", authorGithubId * 1_000_000L + sequence));
        c.setAuthorName(authorName);
        c.setAuthorEmail(authorEmail);
        c.setAuthorGithubId(authorGithubId);
        c.setAuthorGithubLogin(authorName);
        c.setAuthorDate(at);
        c.setMessage("demo commit " + sequence);

        // Deletions occasionally exceed additions so the refactor ratio is not flat zero.
        int additions = 1 + random.nextInt(400);
        int deletions = random.nextInt(random.nextInt(5) == 0 ? 600 : additions + 1);
        c.setAdditions(additions);
        c.setDeletions(deletions);
        c.setFilesChanged(1 + random.nextInt(12));

        if (sequence % SKIP_EVERY == SKIP_EVERY - 1) {
            c.setStatsStatus(StatsStatus.SKIPPED);
            c.setStatsSkipReason(sequence % (SKIP_EVERY * 2) == SKIP_EVERY - 1
                    ? StatsSkipReason.DIFF_TOO_LARGE
                    : StatsSkipReason.RECORD_UNAVAILABLE);
            c.setAdditions(0);
            c.setDeletions(0);
            c.setFilesChanged(0);
        } else {
            c.setStatsStatus(StatsStatus.COMPLETE);
        }
        c.setStatsFetchedAt(at);
        return c;
    }

    /** One in six PRs is left open, so the WIP queue metric has a queue to measure. */
    private static final int OPEN_EVERY = 6;
    /** One in five merged PRs goes unreviewed, so the merge-without-review ratio is not flat zero. */
    private static final int UNREVIEWED_EVERY = 5;

    /** Two to four PRs per week, authored by one account, from the second week of the history on. */
    public static List<GitHubPullRequestEntity> buildPullRequests(long authorGithubId, String authorLogin,
                                                                  LocalDate start, int weeks, int seed) {
        Random random = new Random(seed);
        List<GitHubPullRequestEntity> prs = new ArrayList<>();
        int number = 1;

        // Week 0 is commits only, so every merged PR has commits behind it to claim as its first.
        for (int week = 1; week < weeks; week++) {
            int count = 2 + random.nextInt(3);
            for (int i = 0; i < count; i++) {
                Instant openedAt = start.plusWeeks(week)
                        .plusDays(random.nextInt(7))
                        .atStartOfDay(ZoneOffset.UTC)
                        .plusHours(9 + random.nextInt(9))
                        .toInstant();
                prs.add(pullRequest(authorGithubId, authorLogin, openedAt, number++, random));
            }
        }
        return prs;
    }

    private static GitHubPullRequestEntity pullRequest(long authorGithubId, String authorLogin,
                                                       Instant openedAt, int number, Random random) {
        GitHubPullRequestEntity p = new GitHubPullRequestEntity();
        p.setNumber(number);
        p.setTitle("demo pull request " + number);
        p.setAuthorGithubId(authorGithubId);
        p.setAuthorLogin(authorLogin);
        p.setCreatedAt(openedAt);
        p.setUpdatedAt(openedAt);
        p.setAdditions(10 + random.nextInt(600));
        p.setDeletions(random.nextInt(400));
        p.setChangedFiles(1 + random.nextInt(20));
        p.setCommitsCount(1 + random.nextInt(9));
        p.setCommentsCount(random.nextInt(6));
        p.setReviewCommentsCount(random.nextInt(9));
        p.setStatsStatus(StatsStatus.COMPLETE);
        p.setStatsFetchedAt(openedAt);

        if (number % OPEN_EVERY == 0) {
            p.setState("open");
            p.setMerged(false);
            return p;
        }
        Instant mergedAt = openedAt.plusSeconds((1 + random.nextInt(120)) * 3600L);
        p.setState("closed");
        p.setMerged(true);
        p.setMergedAt(mergedAt);
        p.setClosedAt(mergedAt);
        p.setUpdatedAt(mergedAt);
        p.setLeadTimeHours(Duration.between(openedAt, mergedAt).toHours());
        return p;
    }

    /** One review per PR from the other demo account, skipping some so unreviewed merges exist. */
    public static List<GitHubPrReviewEntity> buildReviews(List<GitHubPullRequestEntity> prs,
                                                          long reviewerGithubId, String reviewerLogin,
                                                          int seed) {
        Random random = new Random(seed);
        List<GitHubPrReviewEntity> reviews = new ArrayList<>();

        for (GitHubPullRequestEntity pr : prs) {
            if (pr.getNumber() % UNREVIEWED_EVERY == 0) continue;

            GitHubPrReviewEntity r = new GitHubPrReviewEntity();
            r.setPullRequest(pr);
            r.setReviewerGithubId(reviewerGithubId);
            r.setReviewerLogin(reviewerLogin);
            r.setState(random.nextInt(4) == 0 ? "CHANGES_REQUESTED" : "APPROVED");
            r.setSubmittedAt(pr.getCreatedAt().plusSeconds(random.nextInt(48 * 3600)));
            reviews.add(r);
        }
        return reviews;
    }

    /** One in seven issues is left open, so "created" and "closed" do not move in lockstep. */
    private static final int ISSUE_OPEN_EVERY = 7;

    /** Three to six issues per week, created and assigned to one account. */
    public static List<IssueEntity> buildIssues(long accountGithubId, String login,
                                                LocalDate start, int weeks, int seed) {
        Random random = new Random(seed);
        List<IssueEntity> issues = new ArrayList<>();
        int number = 1;

        for (int week = 0; week < weeks; week++) {
            int count = 3 + random.nextInt(4);
            for (int i = 0; i < count; i++) {
                Instant createdAt = start.plusWeeks(week)
                        .plusDays(random.nextInt(7))
                        .atStartOfDay(ZoneOffset.UTC)
                        .plusHours(8 + random.nextInt(10))
                        .toInstant();
                issues.add(issue(accountGithubId, login, createdAt, number++, random));
            }
        }
        return issues;
    }

    private static IssueEntity issue(long accountGithubId, String login,
                                     Instant createdAt, int number, Random random) {
        IssueEntity e = new IssueEntity();
        e.setSourceIssueKey("demo/demo-repo#" + number);
        e.setSource(IssueSource.GITHUB);
        e.setTitle("demo issue " + number);
        e.setDescription("Synthetic issue for the demo profile.");
        e.setCreator(login);
        e.setCreatorGithubId(accountGithubId);
        e.setAssignee(login);
        e.setAssigneeGithubId(accountGithubId);
        e.setCreatedAt(createdAt);
        e.setUpdatedAt(createdAt);

        if (number % ISSUE_OPEN_EVERY == 0) {
            e.setState("open");
            return e;
        }
        Instant closedAt = createdAt.plusSeconds((2 + random.nextInt(240)) * 3600L);
        e.setState("closed");
        e.setClosedAt(closedAt);
        e.setUpdatedAt(closedAt);
        return e;
    }

    /** Commits a merged PR claims, matching how a squash-merge message names its pull request. */
    private static final int COMMITS_PER_PR = 3;

    /**
     * Stamps each merged PR's number into the messages of commits that precede its merge.
     *
     * <p>The first-commit-to-merge calculator resolves a PR's commits by looking for {@code #number}
     * in the message, so commits with no such reference leave that metric unproduced. Each commit is
     * claimed at most once, which keeps the claims in chronological order alongside the PR numbers.</p>
     */
    public static void linkCommitsToPullRequests(List<GitCommitEntity> commits,
                                                 List<GitHubPullRequestEntity> prs) {
        Deque<GitCommitEntity> unclaimed = commits.stream()
                .sorted(Comparator.comparing(GitCommitEntity::getAuthorDate))
                .collect(ArrayDeque::new, ArrayDeque::add, ArrayDeque::addAll);

        prs.stream()
                .filter(GitHubPullRequestEntity::isMerged)
                .sorted(Comparator.comparing(GitHubPullRequestEntity::getMergedAt))
                .forEach(pr -> {
                    for (int i = 0; i < COMMITS_PER_PR; i++) {
                        GitCommitEntity head = unclaimed.peekFirst();
                        if (head == null || !head.getAuthorDate().isBefore(pr.getMergedAt())) return;
                        unclaimed.removeFirst();
                        head.setMessage(head.getMessage() + " (#" + pr.getNumber() + ")");
                    }
                });
    }
}
