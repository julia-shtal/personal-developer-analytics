package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.metrics.model.BackfillResult;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives {@link MetricBackfillService} over every user once a night, filling days inside each
 * user's collected history that have never been calculated.
 *
 * <p>Separate from {@link MetricsScheduler}, which only computes yesterday. Gap recovery
 * belongs here because this path has a coverage reference — the {@code metric_coverage}
 * ledger — rather than a high-water mark. The old design in {@code MetricsScheduler} advanced a
 * {@code MAX(date)} watermark past the days its 30-day cap excluded, so those days were never
 * revisited by a later run; this service instead subtracts {@code metric_coverage} from the
 * user's collected history, so the same per-run cap resumes on the next run instead of
 * truncating history permanently.
 *
 * <p>Runs at 03:00 UTC. {@link MetricsScheduler} runs at 01:00 server time with no explicit
 * zone, so in a server zone west of UTC this job can run before that one. That is harmless, not
 * merely tolerated: both paths compute through the same {@link MetricsService#calculateDailyMetrics}
 * upsert guard, so if this job also (re)computes yesterday because the incremental job hasn't
 * run yet, the result is the same row written twice rather than a wrong one. Ordering between
 * the two jobs is therefore not a correctness requirement.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricBackfillScheduler {

    private final UserRepository userRepository;
    private final MetricBackfillService backfillService;

    @Scheduled(cron = "0 0 3 * * ?", zone = "UTC")
    public void backfillAll() {
        log.info("History backfill job started");

        userRepository.findAll().forEach(user -> {
            try {
                BackfillResult result = backfillService.backfillUser(user.getId());
                if (result.daysComputed() > 0) {
                    log.info("Backfill for userId={}: {} day(s) computed, {} remaining",
                            user.getId(), result.daysComputed(), result.daysRemaining());
                }
            } catch (Exception e) {
                // One user's failure must not abort the run — same discipline as MetricsScheduler.
                // The exception is passed as the trailing argument, not e.getMessage(): this job
                // runs unattended overnight, so a failure is not seen until someone notices stale
                // coverage the next day, and getMessage() is null for a NullPointerException and
                // a one-line summary for a wrapped SQLException — losing the failure site itself.
                log.warn("Backfill failed for userId={}", user.getId(), e);
            }
        });

        log.info("History backfill job finished");
    }
}
