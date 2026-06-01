package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.datasource.model.SyncJobStatus;
import com.juliashtal.devanalytics.datasource.repository.SyncJobRepository;
import com.juliashtal.devanalytics.datasource.service.SyncJobStartupMarker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncJobStartupMarkerTest {

    @Mock SyncJobRepository syncJobRepository;
    @InjectMocks SyncJobStartupMarker marker;

    @Test
    void run_marksRunningJobsInterrupted() throws Exception {
        when(syncJobRepository.markInterrupted(eq(SyncJobStatus.RUNNING), eq(SyncJobStatus.INTERRUPTED), any()))
                .thenReturn(3);

        marker.run(new DefaultApplicationArguments());

        ArgumentCaptor<SyncJobStatus> fromCaptor = ArgumentCaptor.forClass(SyncJobStatus.class);
        ArgumentCaptor<SyncJobStatus> toCaptor = ArgumentCaptor.forClass(SyncJobStatus.class);
        verify(syncJobRepository).markInterrupted(fromCaptor.capture(), toCaptor.capture(), any());
        assertThat(fromCaptor.getValue()).isEqualTo(SyncJobStatus.RUNNING);
        assertThat(toCaptor.getValue()).isEqualTo(SyncJobStatus.INTERRUPTED);
    }

    @Test
    void run_noRunningJobs_doesNotThrow() throws Exception {
        when(syncJobRepository.markInterrupted(any(), any(), any())).thenReturn(0);
        marker.run(new DefaultApplicationArguments());
        verify(syncJobRepository).markInterrupted(any(), any(), any());
    }
}
