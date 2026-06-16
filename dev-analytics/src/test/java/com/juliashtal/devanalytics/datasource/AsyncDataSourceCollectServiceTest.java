package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.datasource.service.AsyncDataSourceCollectService;
import com.juliashtal.devanalytics.datasource.service.DataSourceCollectService;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.notification.NotificationDispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncDataSourceCollectServiceTest {

    @Mock DataSourceCollectService collectService;
    @Mock SyncJobTracker tracker;
    @Mock NotificationDispatchService notificationDispatch;

    @InjectMocks AsyncDataSourceCollectService asyncCollectService;

    private static final Long USER_ID = 1L;
    private static final Long DS_ID = 10L;

    @Test
    void collectAsync_success_startsAndCompletesJob() {
        SyncJobTracker.JobState jobState = new SyncJobTracker.JobState();
        when(tracker.start(DS_ID)).thenReturn(jobState);
        when(collectService.collectForDataSource(USER_ID, DS_ID, jobState))
                .thenReturn("owner/repo: 3 commits, 2 PRs.");

        asyncCollectService.collectAsync(USER_ID, DS_ID);

        verify(tracker).complete(DS_ID, "owner/repo: 3 commits, 2 PRs.");
        verify(tracker, never()).fail(any(), any());
        verify(notificationDispatch, never()).sendSyncFailureIfEnabled(any(), any());
    }

    @Test
    void collectAsync_collectThrows_failsJobAndNotifies() {
        SyncJobTracker.JobState jobState = new SyncJobTracker.JobState();
        when(tracker.start(DS_ID)).thenReturn(jobState);
        when(collectService.collectForDataSource(USER_ID, DS_ID, jobState))
                .thenThrow(new RuntimeException("GitHub API rate limited"));

        asyncCollectService.collectAsync(USER_ID, DS_ID);

        verify(tracker).fail(DS_ID, "GitHub API rate limited");
        verify(tracker, never()).complete(any(), any());
        verify(notificationDispatch).sendSyncFailureIfEnabled(USER_ID, DS_ID);
    }
}
