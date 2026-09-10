package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.metrics.model.DailyChurnProjection;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calculates the daily code-churn ratio per repository.
 */
@Component
@RequiredArgsConstructor
public class DailyChurnCalculator implements MetricCalculator {

    private final GitCommitEntityRepository commitRepository;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final MetricSnapshotWriter writer;

    @Override
    public Set<MetricType> produces() {
        return Set.of(MetricType.DAILY_CHURN_RATIO);
    }

    @Override
    public void calculate(MetricCalcContext ctx) {
        if (ctx.repoIds().isEmpty()) return;
        // No identity: attribute nothing rather than everything, so write no rows.
        if (!ctx.identity().hasCommitIdentity()) return;

        List<DailyChurnProjection> rows = commitRepository
                .aggregateChurnDailyByRepoIdsAndIdentity(ctx.repoIds(),
                        ctx.identity().githubUserId(),
                        CalcUtils.emailsOrSentinel(ctx.identity().commitEmails()),
                        ctx.from(), ctx.to());

        Map<Long, GitRepositoryEntity> repoCache = new HashMap<>();
        for (DailyChurnProjection row : rows) {
            LocalDate day = row.getDay().toLocalDate();
            Long repoId   = row.getRepoId();
            long add      = row.getAdditions();
            long del      = row.getDeletions();
            long total    = add + del;
            double churn  = total > 0 ? (double) del / total : 0.0;
            writer.save(ctx.user(), ctx.team(), day, MetricType.DAILY_CHURN_RATIO, churn,
                    repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById), null, null);
        }
    }
}
