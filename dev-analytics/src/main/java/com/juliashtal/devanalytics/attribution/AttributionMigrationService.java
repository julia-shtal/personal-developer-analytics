package com.juliashtal.devanalytics.attribution;

import com.juliashtal.devanalytics.exception.ConflictException;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts and reports on the one-off migration that gives records collected before author
 * attribution their numeric identity columns.
 *
 * <p>The migration exists because every ingest path is incremental. Commit ingest skips hashes it
 * already holds, the PR collector skips PRs whose {@code updated_at} has not moved, and review
 * enrichment never re-runs for a merged PR already {@code COMPLETE}. Adding the columns therefore
 * does nothing for existing rows on its own: without this job the whole history stays
 * unattributed, matching only through whatever addresses the user has declared.
 *
 * <p>Resumable rather than transactional. Each repository is finished independently and only then
 * stamped {@code identity_backfilled_at}; a failure leaves the marker null so the next run retries
 * that repository. Every step is idempotent — commits and PRs are matched by hash and number and
 * skipped when already correct, reviews are replaced wholesale — so retrying costs API calls,
 * never correctness.
 *
 * <p>The work itself runs in {@link AttributionMigrationJob}; this class owns only the
 * in-progress guard and the status projection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttributionMigrationService {

    private final UserRepository userRepository;
    private final GitRepositoryEntityRepository repoRepository;
    private final AttributionMigrationJob job;
    private final Environment environment;

    /**
     * Rejects a second concurrent run. In-process only, which matches this single-instance
     * deployment and the backfill's own in-flight guard; a multi-instance setup would need a
     * database-level lock.
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public AttributionMigrationStatus status() {
        return new AttributionMigrationStatus(
                userRepository.countByGithubLoginIsNotNullAndGithubUserIdIsNull(),
                repoRepository.countByIdentityBackfilledAtIsNull(),
                repoRepository.countByIdentityBackfilledAtIsNotNull(),
                running.get());
    }

    /**
     * Starts a run, rejecting rather than queueing when one is already active.
     *
     * @throws ConflictException if a run is in progress, or the demo profile is enabled
     */
    public void start() {
        // Demo snapshots are seeded, with no commits, PRs or issues behind them: a recompute
        // would delete the demo data and rebuild nothing.
        if (environment.matchesProfiles("demo")) {
            throw new ConflictException("Attribution migration is not available in demo mode");
        }
        if (!running.compareAndSet(false, true)) {
            throw new ConflictException("An attribution migration is already running");
        }

        log.info("Attribution migration starting");
        // The flag is released by the job itself, in a finally, so a failing step cannot leave
        // the migration permanently un-startable.
        job.run(() -> {
            running.set(false);
            log.info("Attribution migration finished");
        });
    }
}
