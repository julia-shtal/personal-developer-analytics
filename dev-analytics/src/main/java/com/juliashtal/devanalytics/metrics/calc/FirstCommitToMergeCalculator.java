package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calculates the first-commit-to-merge duration per pull request.
 */
@Component
@RequiredArgsConstructor
public class FirstCommitToMergeCalculator implements MetricCalculator {

    private final GitHubPullRequestRepository pullRequestRepository;
    private final GitCommitEntityRepository commitRepository;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final MetricSnapshotWriter writer;

    @Override
    public Set<MetricType> produces() {
        return Set.of(MetricType.PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN);
    }

    @Override
    public void calculate(MetricCalcContext ctx) {
        if (ctx.repoIds().isEmpty()) return;
        if (!ctx.identity().hasGithubIdentity()) return;

        List<GitHubPullRequestEntity> prs = pullRequestRepository
                .findMergedPrsByRepoIdsAndAuthorGithubId(ctx.repoIds(), ctx.identity().githubUserId(), ctx.from(), ctx.to());

        Map<Long, List<Long>> perRepo = new HashMap<>();
        for (GitHubPullRequestEntity pr : prs) {
            List<GitCommitEntity> commits = commitRepository.findCommitsForPr(pr.getRepository(), pr.getNumber());
            if (commits.isEmpty() || pr.getMergedAt() == null) continue;
            long hours = Duration.between(commits.get(0).getAuthorDate(), pr.getMergedAt()).toHours();
            pr.setLeadTimeHours(hours);
            perRepo.computeIfAbsent(pr.getRepository().getId(), id -> new ArrayList<>()).add(hours);
        }
        pullRequestRepository.saveAll(prs);

        Map<Long, GitRepositoryEntity> repoCache = new HashMap<>();
        perRepo.forEach((repoId, hours) -> {
            Collections.sort(hours);
            writer.save(ctx.user(), ctx.team(), ctx.fromDate(), MetricType.PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN,
                    CalcUtils.medianOfLongs(hours),
                    repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById), ctx.fromDate(), ctx.toDate());
        });
    }
}
