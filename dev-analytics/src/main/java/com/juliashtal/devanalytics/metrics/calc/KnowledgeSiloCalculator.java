package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.model.RepoCountProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Calculates the knowledge-silo metric per repository.
 */
@Component
@RequiredArgsConstructor
public class KnowledgeSiloCalculator implements MetricCalculator {

    private final GitCommitEntityRepository commitRepository;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final MetricSnapshotWriter writer;

    @Override
    public Set<MetricType> produces() {
        return Set.of(MetricType.KNOWLEDGE_SILO_SCORE);
    }

    @Override
    public void calculate(MetricCalcContext ctx) {
        if (ctx.repoIds().isEmpty()) return;
        // Neither a declared address nor a GitHub account: attribute nothing rather
        // than everything. A calculator without an identity must write no rows.
        if (!ctx.identity().hasCommitIdentity()) return;

        Map<Long, Long> totalByRepo = new HashMap<>();
        for (RepoCountProjection row : commitRepository.countTotalCommitsByRepoIds(ctx.repoIds(), ctx.from(), ctx.to())) {
            totalByRepo.put(row.getRepoId(), row.getCount());
        }
        if (totalByRepo.isEmpty()) return;

        Map<Long, Long> userByRepo = new HashMap<>();
        for (RepoCountProjection row : commitRepository
                .countCommitsByRepoIdsAndIdentity(ctx.repoIds(),
                        ctx.identity().githubUserId(),
                        CalcUtils.emailsOrSentinel(ctx.identity().commitEmails()),
                        ctx.from(), ctx.to())) {
            userByRepo.put(row.getRepoId(), row.getCount());
        }

        double maxShare = 0.0;
        for (Map.Entry<Long, Long> entry : totalByRepo.entrySet()) {
            long total = entry.getValue();
            if (total == 0) continue;
            long userCount = userByRepo.getOrDefault(entry.getKey(), 0L);
            double share = (double) userCount / total;
            if (share > maxShare) maxShare = share;
        }

        writer.save(ctx.user(), ctx.team(), ctx.fromDate(), MetricType.KNOWLEDGE_SILO_SCORE,
                maxShare, null, ctx.fromDate(), ctx.toDate());
    }
}
