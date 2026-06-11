package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.datasource.model.SyncJobEntity;
import com.juliashtal.devanalytics.datasource.repository.SyncJobRepository;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncJobTrackerTest {

    @Mock SyncJobRepository syncJobRepository;
    @InjectMocks SyncJobTracker tracker;

    @Test
    void findLatestPersisted_delegatesToRepository() {
        SyncJobEntity entity = new SyncJobEntity();
        entity.setId(42L);
        entity.setDataSourceId(7L);
        when(syncJobRepository.findTopByDataSourceIdOrderByStartedAtDesc(7L))
                .thenReturn(Optional.of(entity));

        Optional<SyncJobEntity> result = tracker.findLatestPersisted(7L);

        assertThat(result).contains(entity);
    }

    @Test
    void findLatestPersisted_noJobs_returnsEmpty() {
        when(syncJobRepository.findTopByDataSourceIdOrderByStartedAtDesc(7L))
                .thenReturn(Optional.empty());

        assertThat(tracker.findLatestPersisted(7L)).isEmpty();
    }
}
