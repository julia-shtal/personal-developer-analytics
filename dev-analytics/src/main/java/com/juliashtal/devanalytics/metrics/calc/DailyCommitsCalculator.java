package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.metrics.model.DailyCommitsProjection;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calculates the daily commit count and average commit size.
 */
@Component
@RequiredArgsConstructor
public class DailyCommitsCalculator implements MetricCalculator {

    private final GitCommitEntityRepository commitRepository;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final MetricSnapshotWriter writer;

    @Override
    public Set<MetricType> produces() {
        return Set.of(MetricType.DAILY_COMMITS_COUNT, MetricType.DAILY_COMMITS_AVG_SIZE);
    }

    @Override
    public void calculate(MetricCalcContext ctx) {
        if (ctx.repoIds().isEmpty()) return;
        // Neither a declared address nor a GitHub account: attribute nothing rather
        // than everything. A calculator without an identity must write no rows.
        if (!ctx.identity().hasCommitIdentity()) return;

        List<DailyCommitsProjection> rows = commitRepository
                .aggregateCommitsDailyByRepoIdsAndIdentity(ctx.repoIds(),
                        ctx.identity().githubUserId(),
                        CalcUtils.emailsOrSentinel(ctx.identity().commitEmails()),
                        ctx.from(), ctx.to());

        Map<Long, GitRepositoryEntity> repoCache = new HashMap<>();
        for (DailyCommitsProjection row : rows) {
            LocalDate day  = row.getDay().toLocalDate();
            Long repoId    = row.getRepoId();
            long count     = row.getCommitsCount();
            double avgSize = row.getAvgSize() != null ? row.getAvgSize() : 0.0;

            GitRepositoryEntity repo = repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById);
            writer.save(ctx.user(), ctx.team(), day, MetricType.DAILY_COMMITS_COUNT, count, repo, null, null);
            writer.save(ctx.user(), ctx.team(), day, MetricType.DAILY_COMMITS_AVG_SIZE, avgSize, repo, null, null);
        }
    }
}
