package com.juliashtal.devanalytics.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.user.User;
import com.juliashtal.devanalytics.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

import static com.juliashtal.devanalytics.metrics.model.MetricType.*;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MetricSnapshotRepository repository;
    private final GitCommitEntityRepository commitRepository;
    private final GitHubPullRequestRepository pullRequestRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void calculateDailyMetrics(Long userId, LocalDate fromDate, LocalDate toDate) {
        User user = userRepository.getReferenceById(userId);

        Instant from = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        calcDailyCommits(user, from, to);
        calcDailyPrs(user, from, to);
        calcDailyIssues(user, from, to);
        calcDailyChurn(user, from, to);
        calcLeadTimePrs(user, fromDate, from, to);
        calcLeadTimeIssues(user, fromDate, from, to);
        calcLeadTimeFirstCommitToMerge(user, fromDate, from, to);
    }

    private void calcDailyCommits(User user, Instant from, Instant to) {
        List<Object[]> rows = commitRepository.aggregateCommitsDailyPerRepo(user.getId(), from, to);
        Map<LocalDate, LongSummaryStatistics> perDayStats = new HashMap<>();

        for (Object[] row : rows) {
            LocalDate day = ((java.sql.Date) row[0]).toLocalDate();
            Long repoId = ((Number) row[1]).longValue();
            long count = ((Number) row[2]).longValue();
            double avgSize = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;

            GitRepositoryEntity repo = gitRepoRepository.getReferenceById(repoId);

            saveMetric(user, day, DAILY_COMMITS_COUNT, count, repo, null);
            saveMetric(user, day, DAILY_COMMITS_AVG_SIZE, avgSize, repo, null);

            perDayStats
                    .computeIfAbsent(day, d -> new LongSummaryStatistics())
                    .accept(count);
        }

        for (var entry : perDayStats.entrySet()) {
            LocalDate day = entry.getKey();
            long total = entry.getValue().getSum();
            saveMetric(user, day, DAILY_COMMITS_COUNT, total, null, null);
        }
    }

    private void calcDailyPrs(User user, Instant from, Instant to) {
        List<Object[]> created = pullRequestRepository.aggregatePrCreatedDaily(user.getId(), from, to);
        for (Object[] row : created) {
            LocalDate day = ((java.sql.Date) row[0]).toLocalDate();
            long count = ((Number) row[1]).longValue();
            saveMetric(user, day, DAILY_PR_CREATED, count, null, null);
        }

        List<Object[]> merged = pullRequestRepository.aggregatePrMergedDaily(user.getId(), from, to);
        for (Object[] row : merged) {
            LocalDate day = ((java.sql.Date) row[0]).toLocalDate();
            long count = ((Number) row[1]).longValue();
            saveMetric(user, day, DAILY_PR_MERGED, count, null, null);
        }
    }

    private void calcDailyIssues(User user, Instant from, Instant to) {
        var created = issueRepository.aggregateIssuesCreatedDaily(user.getId(), from, to);
        for (Object[] row : created) {
            LocalDate day = ((java.sql.Date) row[0]).toLocalDate();
            long count = ((Number) row[1]).longValue();
            saveMetric(user, day, DAILY_ISSUES_CREATED, count, null, null);
        }

        var closed = issueRepository.aggregateIssuesClosedDaily(user.getId(), from, to);
        for (Object[] row : closed) {
            LocalDate day = ((java.sql.Date) row[0]).toLocalDate();
            long count = ((Number) row[1]).longValue();
            saveMetric(user, day, DAILY_ISSUES_CLOSED, count, null, null);
        }
    }

    private void calcDailyChurn(User user, Instant from, Instant to) {
        var rows = commitRepository.aggregateChurnDaily(user.getId(), from, to);
        for (Object[] row : rows) {
            LocalDate day = ((java.sql.Date) row[0]).toLocalDate();
            long additions = ((Number) row[1]).longValue();
            long deletions = ((Number) row[2]).longValue();
            long total = additions + deletions;
            double churn = total > 0 ? (double) deletions / total : 0.0;

            saveMetric(user, day, DAILY_CHURN_RATIO, churn, null, null);
        }
    }

    private void calcLeadTimePrs(User user, LocalDate fromDate, Instant from, Instant to) {
        var rows = pullRequestRepository.findMergedLeadTimes(user.getId(), from, to);
        var hours = rows.stream()
                .map(row -> {
                    Instant created = (Instant) row[0];
                    Instant merged = (Instant) row[1];
                    return Duration.between(created, merged).toHours();
                })
                .sorted()
                .toList();

        if (hours.isEmpty()) return;

        double median;
        int n = hours.size();
        if (n % 2 == 1) {
            median = hours.get(n / 2);
        } else {
            median = (hours.get(n / 2 - 1) + hours.get(n / 2)) / 2.0;
        }

        saveMetric(user, fromDate, PR_LEAD_TIME_HOURS_MEDIAN, median, null, Map.of(
                "from", fromDate.toString(),
                "to", fromDate.plusDays(1).toString()
        ));
    }

    private void calcLeadTimeIssues(User user, LocalDate fromDate, Instant from, Instant to) {
        var rows = issueRepository.findIssueLeadTimes(user.getId(), from, to);
        var hours = rows.stream()
                .map(row -> {
                    Instant created = (Instant) row[0];
                    Instant closed = (Instant) row[1];
                    return Duration.between(created, closed).toHours();
                })
                .sorted()
                .toList();

        if (hours.isEmpty()) return;

        double median;
        int n = hours.size();
        if (n % 2 == 1) {
            median = hours.get(n / 2);
        } else {
            median = (hours.get(n / 2 - 1) + hours.get(n / 2)) / 2.0;
        }

        saveMetric(user, fromDate, ISSUE_LEAD_TIME_HOURS_MEDIAN, median, null, Map.of(
                "from", fromDate.toString(),
                "to", fromDate.plusDays(1).toString()
        ));
    }

    private void saveMetric(User user,
                            LocalDate date,
                            MetricType metricType,
                            double value,
                            GitRepositoryEntity repo,
                            Map<String, String> dims) {
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setUser(user);
        snapshot.setRepository(repo);
        snapshot.setDate(date);
        snapshot.setMetricType(metricType);
        snapshot.setValue(value);
        if (dims != null && !dims.isEmpty()) {
            try {
                snapshot.setDimensionsJson(objectMapper.writeValueAsString(dims));
            } catch (JsonProcessingException e) {
                snapshot.setDimensionsJson(null);
            }
        }
        repository.save(snapshot);
    }

    private void calcLeadTimeFirstCommitToMerge(User user, LocalDate fromDate, Instant from, Instant to) {
        var prs = pullRequestRepository.findMergedPrsForLeadTime(user.getId(), from, to);
        Map<Long, List<Long>> perRepo = new HashMap<>();

        for (GitHubPullRequestEntity pr : prs) {
            List<GitCommitEntity> commits =
                    commitRepository.findCommitsForPr(pr.getRepository(), pr.getNumber());

            if (commits.isEmpty() || pr.getMergedAt() == null) continue;

            Instant firstCommitTime = commits.get(0).getAuthorDate();
            long hours = Duration.between(firstCommitTime, pr.getMergedAt()).toHours();
            pr.setLeadTimeHours(hours);

            perRepo.computeIfAbsent(pr.getRepository().getId(), id -> new ArrayList<>())
                    .add(hours);
        }
        pullRequestRepository.saveAll(prs);

        List<Long> allHours = new ArrayList<>();

        for (var entry : perRepo.entrySet()) {
            Long repoId = entry.getKey();
            List<Long> hours = entry.getValue();
            Collections.sort(hours);
            double median = medianOfLongs(hours);

            GitRepositoryEntity repo = gitRepoRepository.getReferenceById(repoId);
            saveMetric(user, fromDate,
                    PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN,
                    median,
                    repo,
                    Map.of("from", fromDate.toString(), "to", fromDate.plusDays(1).toString()));

            allHours.addAll(hours);
        }

        if (!allHours.isEmpty()) {
            Collections.sort(allHours);
            double medianAll = medianOfLongs(allHours);
            saveMetric(user, fromDate,
                    PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN,
                    medianAll,
                    null,
                    Map.of("from", fromDate.toString(), "to", fromDate.plusDays(1).toString()));
        }
    }

    private double medianOfLongs(List<Long> values) {
        int n = values.size();
        if (n == 0) return 0.0;
        if (n % 2 == 1) return values.get(n / 2);
        return (values.get(n / 2 - 1) + values.get(n / 2)) / 2.0;
    }

}

