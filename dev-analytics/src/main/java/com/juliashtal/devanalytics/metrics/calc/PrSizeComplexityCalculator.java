package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calculates pull-request size and complexity metrics.
 */
@Component
@RequiredArgsConstructor
public class PrSizeComplexityCalculator implements MetricCalculator {

    private static final int MIN_COMMIT_COUNT = 1;

    private final GitHubPullRequestRepository pullRequestRepository;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final MetricSnapshotWriter writer;

    @Override
    public Set<MetricType> produces() {
        return Set.of(MetricType.PR_SIZE_COMPLEXITY_SCORE);
    }

    @Override
    public void calculate(MetricCalcContext ctx) {
        if (ctx.repoIds().isEmpty()) return;
        if (!ctx.identity().hasGithubIdentity()) return;

        List<GitHubPullRequestEntity> prs = pullRequestRepository
                .findMergedPrsByRepoIdsAndAuthorGithubId(ctx.repoIds(), ctx.identity().githubUserId(), ctx.from(), ctx.to());
        if (prs.isEmpty()) return;

        Map<Long, List<Double>> perRepo = new HashMap<>();
        for (GitHubPullRequestEntity pr : prs) {
            int size = pr.getAdditions() + pr.getDeletions();
            int commits = Math.max(MIN_COMMIT_COUNT, pr.getCommitsCount());
            double complexity = (double) size / commits;
            perRepo.computeIfAbsent(pr.getRepository().getId(), id -> new ArrayList<>()).add(complexity);
        }

        perRepo.forEach((repoId, values) -> {
            Collections.sort(values);
            int n = values.size();
            double median = n % 2 == 1
                    ? values.get(n / 2)
                    : (values.get(n / 2 - 1) + values.get(n / 2)) / 2.0;
            writer.save(ctx.user(), ctx.team(), ctx.fromDate(), MetricType.PR_SIZE_COMPLEXITY_SCORE, median,
                    gitRepoRepository.getReferenceById(repoId), ctx.fromDate(), ctx.toDate());
        });
    }
}
