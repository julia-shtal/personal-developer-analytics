package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPrReviewRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.model.PrReviewTimestampProjection;
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

@Component
@RequiredArgsConstructor
public class ReviewResponseTimeCalculator implements MetricCalculator {

    private final GitHubPullRequestRepository pullRequestRepository;
    private final GitHubPrReviewRepository prReviewRepository;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final MetricSnapshotWriter writer;

    @Override
    public Set<MetricType> produces() {
        return Set.of(MetricType.REVIEW_RESPONSE_TIME_HOURS_MEDIAN);
    }

    @Override
    public void calculate(MetricCalcContext ctx) {
        if (ctx.repoIds().isEmpty()) return;
        if (ctx.user().getGithubLogin() == null) return;

        List<GitHubPullRequestEntity> prs = pullRequestRepository
                .findMergedPrsByRepoIdsAndAuthorLogin(ctx.repoIds(), ctx.user().getGithubLogin(), ctx.from(), ctx.to());
        if (prs.isEmpty()) return;

        List<Long> prIds = prs.stream().map(GitHubPullRequestEntity::getId).toList();
        Map<Long, Instant> firstReviewByPrId = new HashMap<>();
        for (PrReviewTimestampProjection row : prReviewRepository.findFirstReviewTimestampsByPrIds(prIds)) {
            firstReviewByPrId.put(row.getPrId(), row.getReviewedAt());
        }

        Map<Long, List<Long>> perRepo = new HashMap<>();
        for (GitHubPullRequestEntity pr : prs) {
            Instant firstReview = firstReviewByPrId.get(pr.getId());
            if (firstReview == null || pr.getCreatedAt() == null) continue;
            long hours = Duration.between(pr.getCreatedAt(), firstReview).toHours();
            if (hours < 0) continue;
            perRepo.computeIfAbsent(pr.getRepository().getId(), id -> new ArrayList<>()).add(hours);
        }

        Map<Long, GitRepositoryEntity> repoCache = new HashMap<>();
        perRepo.forEach((repoId, values) -> {
            Collections.sort(values);
            writer.save(ctx.user(), ctx.team(), ctx.fromDate(), MetricType.REVIEW_RESPONSE_TIME_HOURS_MEDIAN,
                    CalcUtils.medianOfLongs(values),
                    repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById), ctx.fromDate(), ctx.toDate());
        });
    }
}
