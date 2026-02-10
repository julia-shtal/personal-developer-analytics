package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.metrics.model.MetricAggregateDto;
import com.juliashtal.devanalytics.metrics.model.MetricPointDto;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.user.User;
import com.juliashtal.devanalytics.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static com.juliashtal.devanalytics.metrics.model.MetricType.*;

@RestController
@RequestMapping("/api/metrics")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricSnapshotRepository metricRepo;
    private final MetricSnapshotService metricSnapshotService;
    private final UserRepository userRepo;
    private final GitRepositoryEntityRepository gitRepoRepository;

    @GetMapping("/daily-commits")
    public List<MetricPointDto> getDailyCommits(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        User user = currentUser();

        List<MetricSnapshot> snapshots;
        if (repoId == null) {
            snapshots = metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(
                            user,
                            DAILY_COMMITS_COUNT,
                            from,
                            to
                    ).stream()
                    .filter(s -> s.getRepository() == null)
                    .toList();
        } else {
            GitRepositoryEntity repo = gitRepoRepository.getReferenceById(repoId);
            snapshots = metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateBetween(
                    user,
                    DAILY_COMMITS_COUNT,
                    repo,
                    from,
                    to
            );
        }

        return snapshots.stream()
                .map(MetricPointDto::fromEntity)
                .toList();
    }


    @GetMapping("/daily-churn")
    public List<MetricPointDto> getDailyChurn(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userRepo.getReferenceById(userId);
        return metricRepo.findByUserAndMetricTypeAndDateBetween(user, DAILY_CHURN_RATIO.name(), from, to)
                .stream()
                .map(MetricPointDto::fromEntity)
                .toList();
    }

    @GetMapping("/pr-lead-time")
    public MetricAggregateDto getPrLeadTimeMedian(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        User user = currentUser();
        List<MetricSnapshot> list;

        if (repoId == null) {
            list = metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(
                            user,
                            PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN,
                            from,
                            to
                    ).stream()
                    .filter(s -> s.getRepository() == null)
                    .toList();
        } else {
            GitRepositoryEntity repo = gitRepoRepository.getReferenceById(repoId);
            list = metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateBetween(
                    user,
                    PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN,
                    repo,
                    from,
                    to
            );
        }

        if (list.isEmpty()) {
            return new MetricAggregateDto(PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN.name(), 0.0, null);
        }

        var last = list.stream()
                .max(Comparator.comparing(MetricSnapshot::getDate))
                .orElseThrow();

        return new MetricAggregateDto(
                last.getMetricType().name(),
                last.getValue(),
                last.getDimensionsJson()
        );
    }


    private User currentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        return userRepo.getReferenceById(userId);
    }
}

