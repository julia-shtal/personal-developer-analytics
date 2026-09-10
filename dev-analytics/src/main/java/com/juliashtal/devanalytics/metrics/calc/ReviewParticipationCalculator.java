package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.github.repository.GitHubPrReviewRepository;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Calculates {@link MetricType#REVIEW_PARTICIPATION_COUNT}: the number of distinct PRs
 * the user reviewed (not authored) in the calculation window across all registered repos.
 *
 * <p>Stores one aggregate snapshot ({@code repo = null}) via {@link MetricSnapshotWriter}
 * so the upsert guard prevents duplicate rows across repeated runs.</p>
 */
@Component
@RequiredArgsConstructor
public class ReviewParticipationCalculator implements MetricCalculator {

    private final GitHubPrReviewRepository prReviewRepository;
    private final MetricSnapshotWriter writer;

    @Override
    public Set<MetricType> produces() {
        return Set.of(MetricType.REVIEW_PARTICIPATION_COUNT);
    }

    @Override
    public void calculate(MetricCalcContext ctx) {
        // Guard: no repos registered means no review data to query
        if (ctx.repoIds().isEmpty()) return;
        // Guard: attribution requires a GitHub login
        if (!ctx.identity().hasGithubIdentity()) return;

        long count = prReviewRepository.countDistinctPrsReviewedByUser(
                ctx.identity().githubUserId(),
                ctx.repoIds(),
                ctx.from(),
                ctx.to());

        // Single cross-repo aggregate: repo = null, period shape (periodFrom/To set)
        writer.save(ctx.user(), ctx.team(), ctx.fromDate(),
                MetricType.REVIEW_PARTICIPATION_COUNT, (double) count,
                null, ctx.fromDate(), ctx.toDate());
    }
}
