package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.metrics.model.DailyCountProjection;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DailyPrCalculator implements MetricCalculator {

    private final GitHubPullRequestRepository pullRequestRepository;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final MetricSnapshotWriter writer;

    @Override
    public Set<MetricType> produces() {
        return Set.of(MetricType.DAILY_PR_CREATED, MetricType.DAILY_PR_MERGED);
    }

    @Override
    public void calculate(MetricCalcContext ctx) {
        if (ctx.repoIds().isEmpty()) return;
        if (ctx.user().getGithubLogin() == null) return;

        List<DailyCountProjection> createdRows = pullRequestRepository
                .aggregatePrCreatedDailyByRepoIdsAndAuthorLogin(ctx.repoIds(), ctx.user().getGithubLogin(), ctx.from(), ctx.to());
        List<DailyCountProjection> mergedRows = pullRequestRepository
                .aggregatePrMergedDailyByRepoIdsAndAuthorLogin(ctx.repoIds(), ctx.user().getGithubLogin(), ctx.from(), ctx.to());

        Map<Long, GitRepositoryEntity> repoCache = new HashMap<>();
        for (DailyCountProjection row : createdRows) {
            LocalDate day = row.getDay().toLocalDate();
            Long repoId   = row.getRepoId();
            long count    = row.getCount();
            writer.save(ctx.user(), ctx.team(), day, MetricType.DAILY_PR_CREATED, count,
                    repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById), null, null);
        }

        for (DailyCountProjection row : mergedRows) {
            LocalDate day = row.getDay().toLocalDate();
            Long repoId   = row.getRepoId();
            long count    = row.getCount();
            writer.save(ctx.user(), ctx.team(), day, MetricType.DAILY_PR_MERGED, count,
                    repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById), null, null);
        }
    }
}
