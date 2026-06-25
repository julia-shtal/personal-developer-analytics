package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPrReviewRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.model.PrReviewTimestampProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MergeWithoutReviewCalculator implements MetricCalculator {

    private static final int IDX_UNREVIEWED = 0;
    private static final int IDX_TOTAL      = 1;

    private final GitHubPullRequestRepository pullRequestRepository;
    private final GitHubPrReviewRepository prReviewRepository;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final MetricSnapshotWriter writer;

    @Override
    public Set<MetricType> produces() {
        return Set.of(MetricType.MERGE_WITHOUT_REVIEW_RATIO);
    }

    @Override
    public void calculate(MetricCalcContext ctx) {
        if (ctx.repoIds().isEmpty()) return;
        if (ctx.user().getGithubLogin() == null) return;

        List<GitHubPullRequestEntity> prs = pullRequestRepository
                .findMergedPrsByRepoIdsAndAuthorLogin(ctx.repoIds(), ctx.user().getGithubLogin(), ctx.from(), ctx.to());
        if (prs.isEmpty()) return;

        List<Long> prIds = prs.stream().map(GitHubPullRequestEntity::getId).toList();
        Set<Long> prsWithReviews = prReviewRepository.findFirstReviewTimestampsByPrIds(prIds)
                .stream()
                .map(PrReviewTimestampProjection::getPrId)
                .collect(Collectors.toSet());

        Map<Long, long[]> perRepo = new HashMap<>();
        for (GitHubPullRequestEntity pr : prs) {
            long[] counts = perRepo.computeIfAbsent(pr.getRepository().getId(), id -> new long[]{0, 0});
            counts[IDX_TOTAL]++;
            if (!prsWithReviews.contains(pr.getId())) counts[IDX_UNREVIEWED]++;
        }

        perRepo.forEach((repoId, counts) -> {
            double ratio = counts[IDX_TOTAL] > 0 ? (double) counts[IDX_UNREVIEWED] / counts[IDX_TOTAL] : 0.0;
            writer.save(ctx.user(), ctx.team(), ctx.fromDate(), MetricType.MERGE_WITHOUT_REVIEW_RATIO, ratio,
                    gitRepoRepository.getReferenceById(repoId), ctx.fromDate(), ctx.toDate());
        });
    }
}
