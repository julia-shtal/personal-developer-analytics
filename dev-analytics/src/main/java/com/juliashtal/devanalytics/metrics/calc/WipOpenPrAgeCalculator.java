package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WIP Open PR Age — median age in hours of the user's currently-open PRs, per repository.
 * Point-in-time: age is measured from each PR's createdAt to Instant.now() at calculation time.
 * See docs/metrics/wip-open-pr-age.md.
 */
@Component
@RequiredArgsConstructor
public class WipOpenPrAgeCalculator implements MetricCalculator {

    private final GitHubPullRequestRepository pullRequestRepository;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final MetricSnapshotWriter writer;

    @Override
    public Set<MetricType> produces() {
        return Set.of(MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN);
    }

    @Override
    public void calculate(MetricCalcContext ctx) {
        if (ctx.repoIds().isEmpty()) return;
        if (ctx.user().getGithubLogin() == null) return;

        Instant now = Instant.now();
        List<GitHubPullRequestEntity> openPrs = pullRequestRepository
                .findOpenPrsByRepoIdsAndAuthorLogin(ctx.repoIds(), ctx.user().getGithubLogin());
        if (openPrs.isEmpty()) return;

        Map<Long, List<Long>> agesByRepo = new HashMap<>();
        for (GitHubPullRequestEntity pr : openPrs) {
            long ageHours = Duration.between(pr.getCreatedAt(), now).toHours();
            agesByRepo.computeIfAbsent(pr.getRepository().getId(), id -> new ArrayList<>()).add(ageHours);
        }

        agesByRepo.forEach((repoId, ages) -> {
            Collections.sort(ages);
            double median = CalcUtils.medianOfLongs(ages);
            writer.save(ctx.user(), ctx.team(), ctx.fromDate(),
                    MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN, median,
                    gitRepoRepository.getReferenceById(repoId), ctx.fromDate(), ctx.toDate());
        });
    }
}
