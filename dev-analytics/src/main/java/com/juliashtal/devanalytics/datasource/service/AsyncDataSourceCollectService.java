package com.juliashtal.devanalytics.datasource.service;

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
    private final SyncJobTracker tracker;

    @Async("collectTaskExecutor")
    public void collectAsync(Long userId, Long dataSourceId) {
        SyncJobTracker.JobState jobState = tracker.start(dataSourceId);
        try {
            String result = collectService.collectForDataSource(userId, dataSourceId, jobState);
            tracker.complete(dataSourceId, result);
            log.info("Async collection complete for dataSource={}: {}", dataSourceId, result);
        } catch (Exception e) {
            tracker.fail(dataSourceId, e.getMessage());
            log.error("Async collection failed for dataSource={}: {}", dataSourceId, e.getMessage(), e);
        }
    }
}
