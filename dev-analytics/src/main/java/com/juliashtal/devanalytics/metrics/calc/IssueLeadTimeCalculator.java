package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.metrics.model.IssueLeadTimeProjection;
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
 * Calculates issue lead time (creation to close).
 */
@Component
@RequiredArgsConstructor
public class IssueLeadTimeCalculator implements MetricCalculator {

    private final IssueRepository issueRepository;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final MetricSnapshotWriter writer;

    @Override
    public Set<MetricType> produces() {
        return Set.of(MetricType.ISSUE_LEAD_TIME_HOURS_MEDIAN);
    }

    @Override
    public void calculate(MetricCalcContext ctx) {
        if (ctx.repoIds().isEmpty()) return;
        // Issues match per source; without either identifier this user matches none.
        if (!ctx.identity().hasIssueIdentity()) return;

        List<IssueLeadTimeProjection> rows = issueRepository.findIssueLeadTimesByRepoIdsAndIdentity(
                ctx.repoIds(), ctx.identity().githubUserId(), ctx.identity().jiraAccountId(), ctx.from(), ctx.to());

        Map<Long, List<Long>> perRepo = new HashMap<>();
        for (IssueLeadTimeProjection row : rows) {
            Long repoId     = row.getRepoId();
            Instant created = row.getCreatedAt();
            Instant closed  = row.getClosedAt();
            long hours = Duration.between(created, closed).toHours();
            perRepo.computeIfAbsent(repoId, id -> new ArrayList<>()).add(hours);
        }

        Map<Long, GitRepositoryEntity> repoCache = new HashMap<>();
        perRepo.forEach((repoId, values) -> {
            Collections.sort(values);
            writer.save(ctx.user(), ctx.team(), ctx.fromDate(), MetricType.ISSUE_LEAD_TIME_HOURS_MEDIAN,
                    CalcUtils.medianOfLongs(values),
                    repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById), ctx.fromDate(), ctx.toDate());
        });
    }
}
