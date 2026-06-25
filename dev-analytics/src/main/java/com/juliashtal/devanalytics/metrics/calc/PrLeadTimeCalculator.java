package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.model.PrLeadTimeProjection;
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
public class PrLeadTimeCalculator implements MetricCalculator {

    private final GitHubPullRequestRepository pullRequestRepository;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final MetricSnapshotWriter writer;

    @Override
    public Set<MetricType> produces() {
        return Set.of(MetricType.PR_LEAD_TIME_HOURS_MEDIAN);
    }

    @Override
    public void calculate(MetricCalcContext ctx) {
        if (ctx.repoIds().isEmpty()) return;
        if (ctx.user().getGithubLogin() == null) return;

        List<PrLeadTimeProjection> rows = pullRequestRepository
                .findMergedLeadTimesByRepoIdsAndAuthorLogin(ctx.repoIds(), ctx.user().getGithubLogin(), ctx.from(), ctx.to());

        Map<Long, List<Long>> perRepo = new HashMap<>();
        for (PrLeadTimeProjection row : rows) {
            Long repoId     = row.getRepoId();
            Instant created = row.getCreatedAt();
            Instant merged  = row.getMergedAt();
            long hours = Duration.between(created, merged).toHours();
            perRepo.computeIfAbsent(repoId, id -> new ArrayList<>()).add(hours);
        }

        perRepo.forEach((repoId, values) -> {
            Collections.sort(values);
            writer.save(ctx.user(), ctx.team(), ctx.fromDate(), MetricType.PR_LEAD_TIME_HOURS_MEDIAN,
                    CalcUtils.medianOfLongs(values),
                    gitRepoRepository.getReferenceById(repoId), ctx.fromDate(), ctx.toDate());
        });
    }
}
