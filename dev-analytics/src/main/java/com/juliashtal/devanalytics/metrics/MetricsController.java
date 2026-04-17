package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.metrics.model.MetricAggregateDto;
import com.juliashtal.devanalytics.metrics.model.MetricPointDto;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.juliashtal.devanalytics.metrics.model.MetricType.*;

@RestController
@RequestMapping("/api/metrics")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricSnapshotRepository metricSnapshotRepository;
    private final MetricsService metricsService;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final CheckHelper checkHelper;

    @GetMapping("/calculate")
    public void calculate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        User user = checkHelper.currentUser();
        metricsService.calculateDailyMetrics(user.getId(), from, to);
    }

    @GetMapping("/daily-commits")
    public List<MetricPointDto> getDailyCommits(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        User user = checkHelper.currentUser();
        List<MetricSnapshot> snapshots;

        if (repoId == null) {
            snapshots = metricSnapshotRepository.findByUserAndMetricTypeAndDateBetween(
                            user, DAILY_COMMITS_COUNT, from, to
                    ).stream()
                    .filter(s -> s.getRepository() == null)
                    .collect(Collectors.toList());
        } else {
            GitRepositoryEntity repo = gitRepoRepository.getReferenceById(repoId);
            snapshots = metricSnapshotRepository.findByUserAndMetricTypeAndRepositoryAndDateBetween(
                    user, DAILY_COMMITS_COUNT, repo, from, to
            );
        }

        return snapshots.stream()
                .sorted(Comparator.comparing(MetricSnapshot::getDate))
                .map(MetricPointDto::fromEntity)
                .toList();
    }

    @GetMapping("/daily-pr-created")
    public List<MetricPointDto> getDailyPrCreated(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getDailyPrMetric(DAILY_PR_CREATED, from, to, repoId);
    }

    @GetMapping("/daily-pr-merged")
    public List<MetricPointDto> getDailyPrMerged(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getDailyPrMetric(DAILY_PR_MERGED, from, to, repoId);
    }

    private List<MetricPointDto> getDailyPrMetric(MetricType metricType,
                                                  LocalDate from,
                                                  LocalDate to,
                                                  Long repoId) {
        User user = checkHelper.currentUser();
        List<MetricSnapshot> snapshots;

        if (repoId == null) {
            snapshots = metricSnapshotRepository.findByUserAndMetricTypeAndDateBetween(
                            user, metricType, from, to
                    ).stream()
                    .filter(s -> s.getRepository() == null)
                    .collect(Collectors.toList());
        } else {
            GitRepositoryEntity repo = gitRepoRepository.getReferenceById(repoId);
            snapshots = metricSnapshotRepository.findByUserAndMetricTypeAndRepositoryAndDateBetween(
                    user, metricType, repo, from, to
            );
        }

        return snapshots.stream()
                .sorted(Comparator.comparing(MetricSnapshot::getDate))
                .map(MetricPointDto::fromEntity)
                .toList();
    }

    @GetMapping("/daily-issues-closed")
    public List<MetricPointDto> getDailyIssuesClosed(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getDailyIssueMetric(DAILY_ISSUES_CLOSED, from, to, repoId);
    }

    @GetMapping("/daily-issues-created")
    public List<MetricPointDto> getDailyIssuesCreated(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getDailyIssueMetric(DAILY_ISSUES_CREATED, from, to, repoId);
    }

    private List<MetricPointDto> getDailyIssueMetric(MetricType metricType,
                                                     LocalDate from,
                                                     LocalDate to,
                                                     Long repoId) {
        User user = checkHelper.currentUser();
        List<MetricSnapshot> snapshots;

        if (repoId == null) {
            snapshots = metricSnapshotRepository.findByUserAndMetricTypeAndDateBetween(
                            user, metricType, from, to
                    ).stream()
                    .filter(s -> s.getRepository() == null)
                    .collect(Collectors.toList());
        } else {
            GitRepositoryEntity repo = gitRepoRepository.getReferenceById(repoId);
            snapshots = metricSnapshotRepository.findByUserAndMetricTypeAndRepositoryAndDateBetween(
                    user, metricType, repo, from, to
            );
        }

        return snapshots.stream()
                .sorted(Comparator.comparing(MetricSnapshot::getDate))
                .map(MetricPointDto::fromEntity)
                .toList();
    }

    @GetMapping("/daily-churn")
    public List<MetricPointDto> getDailyChurn(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        User user = checkHelper.currentUser();
        List<MetricSnapshot> snapshots;

        if (repoId == null) {
            snapshots = metricSnapshotRepository.findByUserAndMetricTypeAndDateBetween(
                            user, DAILY_CHURN_RATIO, from, to
                    ).stream()
                    .filter(s -> s.getRepository() == null)
                    .collect(Collectors.toList());
        } else {
            GitRepositoryEntity repo = gitRepoRepository.getReferenceById(repoId);
            snapshots = metricSnapshotRepository.findByUserAndMetricTypeAndRepositoryAndDateBetween(
                    user, DAILY_CHURN_RATIO, repo, from, to
            );
        }

        return snapshots.stream()
                .sorted(Comparator.comparing(MetricSnapshot::getDate))
                .map(MetricPointDto::fromEntity)
                .toList();
    }

    @GetMapping("/pr-lead-time")
    public MetricAggregateDto getPrLeadTimeMedian(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getLeadTimeAggregate(PR_LEAD_TIME_HOURS_MEDIAN, from, to, repoId);
    }

    @GetMapping("/pr-first-commit-lead-time")
    public MetricAggregateDto getPrFirstCommitLeadTimeMedian(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getLeadTimeAggregate(PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN, from, to, repoId);
    }

    @GetMapping("/review-response-time")
    public MetricAggregateDto getReviewResponseTimeMedian(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getLeadTimeAggregate(REVIEW_RESPONSE_TIME_HOURS_MEDIAN, from, to, repoId);
    }

    @GetMapping("/focus-ratio/series")
    public List<MetricPointDto> getFocusRatioSeries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        User user = checkHelper.currentUser();
        return metricSnapshotRepository.findByUserAndMetricTypeAndDateBetween(
                        user, FOCUS_RATIO_DAYS_TASKS, from, to
                ).stream()
                .sorted(Comparator.comparing(MetricSnapshot::getDate))
                .map(MetricPointDto::fromEntity)
                .toList();
    }

    @GetMapping("/focus-ratio")
    public MetricAggregateDto getFocusRatioAggregate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        User user = checkHelper.currentUser();
        var list = metricSnapshotRepository.findByUserAndMetricTypeAndDateBetween(
                user, FOCUS_RATIO_DAYS_TASKS, from, to
        );

        if (list.isEmpty()) {
            return new MetricAggregateDto(FOCUS_RATIO_DAYS_TASKS, 0.0, null, null);
        }

        double avg = list.stream()
                .mapToDouble(MetricSnapshot::getValue)
                .average()
                .orElse(0.0);

        return new MetricAggregateDto(FOCUS_RATIO_DAYS_TASKS, avg, null, null);
    }

    @GetMapping("/issue-lead-time")
    public MetricAggregateDto getIssueLeadTimeMedian(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        User user = checkHelper.currentUser();
        List<MetricSnapshot> list;

        if (repoId == null) {
            list = metricSnapshotRepository.findByUserAndMetricTypeAndDateBetween(
                            user, ISSUE_LEAD_TIME_HOURS_MEDIAN, from, to
                    ).stream()
                    .filter(s -> s.getRepository() == null)
                    .toList();
        } else {
            GitRepositoryEntity repo = gitRepoRepository.getReferenceById(repoId);
            list = metricSnapshotRepository.findByUserAndMetricTypeAndRepositoryAndDateBetween(
                    user, ISSUE_LEAD_TIME_HOURS_MEDIAN, repo, from, to
            );
        }

        if (list.isEmpty()) {
            return new MetricAggregateDto(ISSUE_LEAD_TIME_HOURS_MEDIAN, 0.0, null, null);
        }

        MetricSnapshot last = list.stream()
                .max(Comparator.comparing(MetricSnapshot::getDate))
                .orElseThrow();

        return new MetricAggregateDto(
                last.getMetricType(),
                last.getValue(),
                last.getPeriodFrom(),
                last.getPeriodTo()
        );
    }

    private MetricAggregateDto getLeadTimeAggregate(MetricType metricType,
                                                    LocalDate from,
                                                    LocalDate to,
                                                    Long repoId) {
        User user = checkHelper.currentUser();
        List<MetricSnapshot> list;

        if (repoId == null) {
            list = metricSnapshotRepository.findByUserAndMetricTypeAndDateBetween(
                            user, metricType, from, to
                    ).stream()
                    .filter(s -> s.getRepository() == null)
                    .collect(Collectors.toList());
        } else {
            GitRepositoryEntity repo = gitRepoRepository.getReferenceById(repoId);
            list = metricSnapshotRepository.findByUserAndMetricTypeAndRepositoryAndDateBetween(
                    user, metricType, repo, from, to
            );
        }

        if (list.isEmpty()) {
            return new MetricAggregateDto(metricType, 0.0, null, null);
        }

        MetricSnapshot last = list.stream()
                .max(Comparator.comparing(MetricSnapshot::getDate))
                .orElseThrow();

        return new MetricAggregateDto(
                last.getMetricType(),
                last.getValue(),
                last.getPeriodFrom(),
                last.getPeriodTo()
        );
    }
}