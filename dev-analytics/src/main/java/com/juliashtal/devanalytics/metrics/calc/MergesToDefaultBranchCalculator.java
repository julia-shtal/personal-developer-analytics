package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.model.PrLeadTimeProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calculates {@link MetricType#MERGES_TO_DEFAULT_BRANCH_PER_WEEK}: a DORA
 * deployment-frequency proxy counting pull requests the user merged into the repository's
 * default branch, bucketed by UTC-calendar-day-derived ISO week, per repository.
 *
 * <p>A merge is not a deployment — see docs/metrics/merges-to-default-branch-per-week.md.</p>
 */
@Component
@RequiredArgsConstructor
public class MergesToDefaultBranchCalculator implements MetricCalculator {

    private final GitHubPullRequestRepository pullRequestRepository;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final MetricSnapshotWriter writer;

    @Override
    public Set<MetricType> produces() {
        return Set.of(MetricType.MERGES_TO_DEFAULT_BRANCH_PER_WEEK);
    }

    @Override
    public void calculate(MetricCalcContext ctx) {
        if (ctx.repoIds().isEmpty()) return;
        if (!ctx.identity().hasGithubIdentity()) return;

        List<PrLeadTimeProjection> rows = pullRequestRepository
                .findMergedToDefaultBranchByRepoIdsAndAuthorGithubId(
                        ctx.repoIds(), ctx.identity().githubUserId(), ctx.from(), ctx.to());
        if (rows.isEmpty()) return;

        // repoId -> (isoWeekMonday -> mergeCount)
        Map<Long, Map<LocalDate, Long>> perRepoPerWeek = new HashMap<>();
        for (PrLeadTimeProjection row : rows) {
            LocalDate mergedDay = row.getMergedAt().atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate weekStart = mergedDay.with(WeekFields.ISO.dayOfWeek(), 1);
            perRepoPerWeek
                    .computeIfAbsent(row.getRepoId(), id -> new HashMap<>())
                    .merge(weekStart, 1L, Long::sum);
        }

        perRepoPerWeek.forEach((repoId, byWeek) -> {
            GitRepositoryEntity repo = gitRepoRepository.getReferenceById(repoId);
            byWeek.forEach((weekStart, count) ->
                    writer.save(ctx.user(), ctx.team(), weekStart,
                            MetricType.MERGES_TO_DEFAULT_BRANCH_PER_WEEK, (double) count,
                            repo, weekStart, weekStart.plusDays(6)));
        });
    }
}
