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
 * <p>Needed because every ingest path is incremental and so never revisits existing rows.
 * Resumable rather than transactional: each repository is stamped {@code identity_backfilled_at}
 * only once finished, and every step is idempotent, so a retry costs API calls but not
 * correctness. The work itself runs in {@link AttributionMigrationJob}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttributionMigrationService {

    private final UserRepository userRepository;
    private final GitRepositoryEntityRepository repoRepository;
    private final AttributionMigrationJob job;
    private final Environment environment;

    /** Rejects a second concurrent run. In-process only, matching the backfill's in-flight guard. */
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
        // Demo snapshots are seeded with no records behind them, so a recompute would erase them.
        if (environment.matchesProfiles("demo")) {
            throw new ConflictException("Attribution migration is not available in demo mode");
        }
        if (!running.compareAndSet(false, true)) {
            throw new ConflictException("An attribution migration is already running");
        }

        log.info("Attribution migration starting");
        // Released by the job in a finally, so a failing step cannot leave this un-startable.
        job.run(() -> {
            running.set(false);
            log.info("Attribution migration finished");
        });
    }
}
