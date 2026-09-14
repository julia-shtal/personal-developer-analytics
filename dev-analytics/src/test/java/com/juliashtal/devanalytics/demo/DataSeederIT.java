package com.juliashtal.devanalytics.demo;

import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.repository.MetricSnapshotRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that the demo profile demonstrates the pipeline rather than bypassing it: the snapshots it
 * shows are produced by the production calculators from seeded activity, so a calculator that
 * stops producing a metric shows up here as a missing type.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("demo")
class DataSeederIT {

    @Autowired UserRepository            userRepository;
    @Autowired MetricSnapshotRepository  snapshotRepository;
    @Autowired GitCommitEntityRepository commitRepository;
    @Autowired GitHubPullRequestRepository pullRequestRepository;
    @Autowired IssueRepository           issueRepository;
    @Autowired DataSeeder                seeder;

    @Test
    void demoUserIsCreatedOnStartup() {
        assertThat(userRepository.findByEmail("demo@demo.com")).isPresent();
    }

    @Test
    void activityIsSeededForBothDemoAccounts() {
        assertThat(commitRepository.count()).isPositive();
        assertThat(pullRequestRepository.count()).isPositive();
        assertThat(issueRepository.count()).isPositive();
    }

    @Test
    void snapshotsAreComputedFromSeededActivity() {
        var user = userRepository.findByEmail("demo@demo.com").orElseThrow();

        assertThat(snapshotRepository.countByUserId(user.getId())).isPositive();
    }

    @Test
    void everyCoreMetricTypeIsProducedByTheCalculators() {
        var user = userRepository.findByEmail("demo@demo.com").orElseThrow();
        Set<MetricType> produced = snapshotRepository.findAll().stream()
                .filter(s -> s.getUser().getId().equals(user.getId()))
                .map(MetricSnapshot::getMetricType)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(produced).contains(
                MetricType.DAILY_COMMITS_COUNT,
                MetricType.DAILY_COMMITS_AVG_SIZE,
                MetricType.DAILY_PR_CREATED,
                MetricType.DAILY_PR_MERGED,
                MetricType.DAILY_ISSUES_CREATED,
                MetricType.DAILY_ISSUES_CLOSED,
                MetricType.DAILY_CHURN_RATIO,
                MetricType.FOCUS_RATIO_DAYS_TASKS,
                MetricType.PR_LEAD_TIME_HOURS_MEDIAN,
                MetricType.PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN,
                MetricType.ISSUE_LEAD_TIME_HOURS_MEDIAN,
                MetricType.REVIEW_RESPONSE_TIME_HOURS_MEDIAN,
                MetricType.REVIEW_PARTICIPATION_COUNT,
                MetricType.REFACTOR_RATIO,
                MetricType.PR_SIZE_COMPLEXITY_SCORE,
                MetricType.COMMITS_PER_WEEK_AVG,
                MetricType.DEEP_WORK_STREAK_DAYS,
                MetricType.KNOWLEDGE_SILO_SCORE,
                MetricType.AFTER_HOURS_COMMIT_RATIO,
                MetricType.MERGE_WITHOUT_REVIEW_RATIO,
                MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN);
    }

    @Test
    void seederIsIdempotent() {
        var user    = userRepository.findByEmail("demo@demo.com").orElseThrow();
        long before = snapshotRepository.countByUserId(user.getId());
        seeder.run(null);
        long after  = snapshotRepository.countByUserId(user.getId());

        assertThat(after).isEqualTo(before);
    }
}
