package com.juliashtal.devanalytics.datasource.service;

import com.juliashtal.devanalytics.metrics.service.MetricBackfillTrigger;
import com.juliashtal.devanalytics.notification.NotificationDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Runs data-source collection on the {@code collectTaskExecutor} thread pool
 * so the HTTP request returns immediately with 202 Accepted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncDataSourceCollectService {

    private final DataSourceCollectService collectService;
    private final DataSourceService dataSourceService;
    private final SyncJobTracker tracker;
    private final NotificationDispatchService notificationDispatch;
    private final MetricBackfillTrigger backfillTrigger;

    @Async("collectTaskExecutor")
    public void collectAsync(Long userId, Long dataSourceId) {
        SyncJobTracker.JobState jobState = tracker.start(dataSourceId);
        boolean firstCollection;
        try {
            // Read before collecting: collectForDataSource stamps lastSuccessSync on success, so
            // afterwards every run looks like a repeat one. This lookup is inside the try, not
            // before tracker.start, so a failure here (the config was deleted, or the user lost
            // access, between the controller's 202 and this task running) is reported through
            // the exact same tracker.fail + sendSyncFailureIfEnabled path as any other collection
            // failure, rather than escaping this @Async void method silently.
            firstCollection =
                    dataSourceService.getForUser(userId, dataSourceId).getLastSuccessSync() == null;

            String result = collectService.collectForDataSource(userId, dataSourceId, jobState);
            tracker.complete(dataSourceId, result);
            log.info("Async collection complete for dataSource={}: {}", dataSourceId, result);
        } catch (Exception e) {
            tracker.fail(dataSourceId, e.getMessage());
            log.error("Async collection failed for dataSource={}: {}", dataSourceId, e.getMessage(), e);
            notificationDispatch.sendSyncFailureIfEnabled(userId, dataSourceId);
            return;
        }

        if (firstCollection) {
            // Already off the request thread on collectTaskExecutor, so no second executor is
            // needed. Failures here are logged rather than propagated: the collection did
            // succeed, and the nightly backfill job will pick the history up regardless.
            try {
                backfillTrigger.onFirstCollection(userId);
            } catch (Exception e) {
                log.warn("Post-collection backfill failed for userId={}: {}", userId, e.getMessage());
            }
        }
    }
}
