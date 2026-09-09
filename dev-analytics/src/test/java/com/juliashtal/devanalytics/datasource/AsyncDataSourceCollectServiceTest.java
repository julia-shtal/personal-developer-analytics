package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.service.AsyncDataSourceCollectService;
import com.juliashtal.devanalytics.datasource.service.DataSourceCollectService;
import com.juliashtal.devanalytics.datasource.service.DataSourceService;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.metrics.service.MetricBackfillTrigger;
import com.juliashtal.devanalytics.notification.NotificationDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncDataSourceCollectServiceTest {

    @Mock DataSourceCollectService collectService;
    @Mock DataSourceService dataSourceService;
    @Mock SyncJobTracker tracker;
    @Mock NotificationDispatchService notificationDispatch;
    @Mock MetricBackfillTrigger backfillTrigger;

    AsyncDataSourceCollectService service;
    DataSourceConfig config;

    @BeforeEach
    void setUp() {
        service = new AsyncDataSourceCollectService(
                collectService, dataSourceService, tracker, notificationDispatch, backfillTrigger);
        config = new DataSourceConfig();
    }

    @Test
    void collectAsync_firstSuccessfulCollection_triggersBackfill() {
        when(dataSourceService.getForUser(1L, 5L)).thenReturn(config);
        when(tracker.start(5L)).thenReturn(null);
        config.setLastSuccessSync(null);
        when(collectService.collectForDataSource(eq(1L), eq(5L), any())).thenReturn("10 items collected");

        service.collectAsync(1L, 5L);

        verify(backfillTrigger).onFirstCollection(1L);
    }

    @Test
    void collectAsync_sourceAlreadySynced_doesNotTriggerBackfill() {
        when(dataSourceService.getForUser(1L, 5L)).thenReturn(config);
        when(tracker.start(5L)).thenReturn(null);
        config.setLastSuccessSync(Instant.parse("2026-03-01T00:00:00Z"));
        when(collectService.collectForDataSource(eq(1L), eq(5L), any())).thenReturn("10 items collected");

        service.collectAsync(1L, 5L);

        verify(backfillTrigger, never()).onFirstCollection(anyLong());
    }

    @Test
    void collectAsync_collectionFails_doesNotTriggerBackfill() {
        when(dataSourceService.getForUser(1L, 5L)).thenReturn(config);
        when(tracker.start(5L)).thenReturn(null);
        config.setLastSuccessSync(null);
        when(collectService.collectForDataSource(eq(1L), eq(5L), any()))
                .thenThrow(new IllegalStateException("boom"));

        service.collectAsync(1L, 5L);

        verify(backfillTrigger, never()).onFirstCollection(anyLong());
        verify(notificationDispatch).sendSyncFailureIfEnabled(1L, 5L);
    }

    @Test
    void collectAsync_backfillFails_collectionStillReportsSuccess() {
        when(dataSourceService.getForUser(1L, 5L)).thenReturn(config);
        when(tracker.start(5L)).thenReturn(null);
        config.setLastSuccessSync(null);
        when(collectService.collectForDataSource(eq(1L), eq(5L), any())).thenReturn("10 items collected");
        doThrow(new IllegalStateException("boom")).when(backfillTrigger).onFirstCollection(1L);

        service.collectAsync(1L, 5L);

        verify(tracker).complete(5L, "10 items collected");
        verify(notificationDispatch, never()).sendSyncFailureIfEnabled(anyLong(), anyLong());
    }

    /**
     * Guards against the lookup-before-tracker.start defect: reading {@code lastSuccessSync} to
     * decide whether this is a first collection must never escape as an uncaught exception. If
     * that read is moved back outside the try/catch that owns {@code tracker.fail} +
     * {@code sendSyncFailureIfEnabled}, this test fails because neither is invoked and the
     * exception propagates out of the {@code @Async void} method instead.
     */
    @Test
    void collectAsync_dataSourceLookupFails_reportsFailureAndNotifies() {
        when(tracker.start(5L)).thenReturn(null);
        when(dataSourceService.getForUser(1L, 5L))
                .thenThrow(new NoSuchElementException("DataSource not found: 5"));

        service.collectAsync(1L, 5L);

        verify(tracker).fail(eq(5L), any());
        verify(notificationDispatch).sendSyncFailureIfEnabled(1L, 5L);
        verify(backfillTrigger, never()).onFirstCollection(anyLong());
        verify(collectService, never()).collectForDataSource(anyLong(), anyLong(), any());
    }
}
