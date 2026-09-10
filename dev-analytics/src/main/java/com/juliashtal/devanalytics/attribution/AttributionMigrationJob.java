package com.juliashtal.devanalytics.attribution;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.service.GitHubIdentityBackfill;
import com.juliashtal.devanalytics.jira.service.JiraIdentityBackfill;
import com.juliashtal.devanalytics.metrics.service.MetricBackfillTrigger;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.user.service.AuthorIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * The four migration steps, in order, on a pool thread.
 *
 * <p>A separate bean from {@link AttributionMigrationService} so {@code @Async} actually applies —
 * a self-invocation would bypass the Spring proxy and run synchronously on the request thread.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttributionMigrationJob {

    private final UserRepository userRepository;
    private final GitRepositoryEntityRepository repoRepository;
    private final GitHubIdentityBackfill githubBackfill;
    private final JiraIdentityBackfill jiraBackfill;
    private final AuthorIdentityService authorIdentityService;
    private final MetricBackfillTrigger backfillTrigger;

    /**
     * @param onFinish always invoked, so the caller's in-progress flag is released even when a
     *                 step throws — otherwise a single failure would block every later run.
     */
    @Async("collectTaskExecutor")
    public void run(Runnable onFinish) {
        try {
            resolveUserIdentities();
            long stillPending = backfillGithubRepositories();
            jiraBackfill.recollectAllProjects();
            recomputeIfComplete(stillPending);
        } catch (RuntimeException e) {
            log.error("Attribution migration aborted", e);
        } finally {
            onFinish.run();
        }
    }

    // -------------------------------------------------------------------------
    // Step 1 — users
    // -------------------------------------------------------------------------

    private void resolveUserIdentities() {
        List<User> unresolved = userRepository.findByGithubLoginIsNotNullAndGithubUserIdIsNull();
        log.info("Attribution migration: resolving {} GitHub logins", unresolved.size());

        for (User user : unresolved) {
            try {
                authorIdentityService.setGithubIdentity(user.getId(), user.getGithubLogin());
            } catch (RuntimeException e) {
                // A dead or already-claimed login is data, not a failure: that user keeps
                // matching through their declared addresses and the rest still resolve.
                log.warn("Could not resolve the GitHub login for userId={}: {}",
                        user.getId(), e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Step 2 — GitHub repositories
    // -------------------------------------------------------------------------

    /** @return how many repositories are still pending once this pass finishes */
    private long backfillGithubRepositories() {
        List<GitRepositoryEntity> pending = repoRepository.findPendingIdentityBackfill();
        log.info("Attribution migration: {} GitHub repositories pending", pending.size());

        for (GitRepositoryEntity repo : pending) {
            try {
                githubBackfill.backfillCommits(repo);
                githubBackfill.backfillPullRequests(repo);
                githubBackfill.refreshReviews(repo);
                githubBackfill.backfillIssues(repo);

                // Stamped only after all four succeeded; the marker is the only durable progress.
                repo.setIdentityBackfilledAt(Instant.now());
                repoRepository.save(repo);
                log.info("Attribution migration: {} complete", repo.getName());
            } catch (RuntimeException e) {
                // Marker stays null on purpose, so the next run retries this repository.
                log.error("Attribution migration failed for repoId={} ({}); it stays pending",
                        repo.getId(), repo.getName(), e);
            }
        }

        return repoRepository.countByIdentityBackfilledAtIsNull();
    }

    // -------------------------------------------------------------------------
    // Step 4 — recompute
    // -------------------------------------------------------------------------

    private void recomputeIfComplete(long stillPending) {
        if (stillPending > 0) {
            log.warn("Attribution migration: {} repositories still pending, skipping the metric "
                    + "recompute. Re-run the migration; recomputing now would rebuild metrics over "
                    + "a history that is only partly attributed, and the coverage ledger would then "
                    + "mark those days done.", stillPending);
            return;
        }

        List<User> users = userRepository.findAll();
        log.info("Attribution migration: recomputing metrics for {} users", users.size());
        for (User user : users) {
            try {
                backfillTrigger.onAttributionChanged(user.getId());
            } catch (RuntimeException e) {
                log.error("Metric recompute failed for userId={} during attribution migration",
                        user.getId(), e);
            }
        }
    }
}
