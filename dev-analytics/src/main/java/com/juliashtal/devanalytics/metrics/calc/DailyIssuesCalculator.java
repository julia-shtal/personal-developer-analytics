package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.metrics.model.DailyCountProjection;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calculates daily issues created and closed.
 */
@Component
@RequiredArgsConstructor
public class DailyIssuesCalculator implements MetricCalculator {

    private final IssueRepository issueRepository;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final MetricSnapshotWriter writer;

    @Override
    public Set<MetricType> produces() {
        return Set.of(MetricType.DAILY_ISSUES_CREATED, MetricType.DAILY_ISSUES_CLOSED);
    }

    @Override
    public void calculate(MetricCalcContext ctx) {
        if (ctx.repoIds().isEmpty()) return;

        List<DailyCountProjection> createdRows = issueRepository.aggregateIssuesCreatedDailyByRepoIds(ctx.repoIds(), ctx.from(), ctx.to());
        List<DailyCountProjection> closedRows  = issueRepository.aggregateIssuesClosedDailyByRepoIds(ctx.repoIds(), ctx.from(), ctx.to());

        Map<Long, GitRepositoryEntity> repoCache = new HashMap<>();
        for (DailyCountProjection row : createdRows) {
            LocalDate day = row.getDay().toLocalDate();
            Long repoId   = row.getRepoId();
            long count    = row.getCount();
            writer.save(ctx.user(), ctx.team(), day, MetricType.DAILY_ISSUES_CREATED, count,
                    repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById), null, null);
        }

        for (DailyCountProjection row : closedRows) {
            LocalDate day = row.getDay().toLocalDate();
            Long repoId   = row.getRepoId();
            long count    = row.getCount();
            writer.save(ctx.user(), ctx.team(), day, MetricType.DAILY_ISSUES_CLOSED, count,
                    repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById), null, null);
        }
    }
}
