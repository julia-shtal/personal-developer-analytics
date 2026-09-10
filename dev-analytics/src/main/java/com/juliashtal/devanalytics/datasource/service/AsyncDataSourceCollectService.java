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
            // Read before collecting, since collectForDataSource stamps lastSuccessSync on success.
            // Inside the try so a failure here reports through tracker.fail like any other.
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
            // Already off the request thread. Logged rather than propagated: the collection did
            // succeed, and the nightly backfill picks the history up regardless.
            try {
                backfillTrigger.onFirstCollection(userId);
            } catch (Exception e) {
                log.warn("Post-collection backfill failed for userId={}: {}", userId, e.getMessage());
            }
        }
    }
}
