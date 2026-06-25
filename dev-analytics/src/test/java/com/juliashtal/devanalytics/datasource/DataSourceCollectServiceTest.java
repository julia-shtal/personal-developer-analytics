package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.datasource.collect.SourceCollector;
import com.juliashtal.devanalytics.datasource.collect.SourceCollectorRegistry;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.datasource.service.DataSourceCollectService;
import com.juliashtal.devanalytics.datasource.service.DataSourceService;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSourceCollectServiceTest {

    @Mock DataSourceConfigRepository configRepository;
    @Mock DataSourceService dataSourceService;
    @Mock SourceCollectorRegistry collectorRegistry;
    @Mock SyncJobTracker tracker;

    @InjectMocks DataSourceCollectService collectService;

    private static final Long USER_ID = 1L;
    private static final Long DS_ID   = 10L;

    private DataSourceConfig cfg(DataSourceType type) {
        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(DS_ID);
        cfg.setType(type);
        return cfg;
    }

    @Test
    void collectForDataSource_delegatesToRegistry_andSetsLastSuccessSync() {
        DataSourceConfig cfg = cfg(DataSourceType.GITHUB);
        when(dataSourceService.getForUser(USER_ID, DS_ID)).thenReturn(cfg);

        SourceCollector stubCollector = new SourceCollector() {
            @Override public DataSourceType supports() { return DataSourceType.GITHUB; }
            @Override public int collect(DataSourceConfig c, SyncJobTracker.JobState js) { return 5; }
            @Override public int phaseCount(DataSourceConfig c) { return 2; }
        };
        when(collectorRegistry.forType(DataSourceType.GITHUB)).thenReturn(stubCollector);
        when(configRepository.save(cfg)).thenReturn(cfg);

        SyncJobTracker.JobState jobState = new SyncJobTracker.JobState();
        collectService.collectForDataSource(USER_ID, DS_ID, jobState);

        assertThat(jobState.totalPhases).isEqualTo(2);
        assertThat(cfg.getLastSuccessSync()).isNotNull();
        verify(configRepository).save(cfg);
    }

    @Test
    void collectForDataSource_collectorReturnsZero_summaryIsNothingToCollect() {
        DataSourceConfig cfg = cfg(DataSourceType.GIT_LOCAL);
        when(dataSourceService.getForUser(USER_ID, DS_ID)).thenReturn(cfg);

        SourceCollector stubCollector = new SourceCollector() {
            @Override public DataSourceType supports() { return DataSourceType.GIT_LOCAL; }
            @Override public int collect(DataSourceConfig c, SyncJobTracker.JobState js) { return 0; }
        };
        when(collectorRegistry.forType(DataSourceType.GIT_LOCAL)).thenReturn(stubCollector);
        when(configRepository.save(cfg)).thenReturn(cfg);

        String result = collectService.collectForDataSource(USER_ID, DS_ID, null);

        assertThat(result).isEqualTo("Nothing to collect (no repos registered)");
    }

    @Test
    void collectForDataSource_nullJobState_doesNotThrow() {
        DataSourceConfig cfg = cfg(DataSourceType.JIRA);
        when(dataSourceService.getForUser(USER_ID, DS_ID)).thenReturn(cfg);

        SourceCollector stubCollector = new SourceCollector() {
            @Override public DataSourceType supports() { return DataSourceType.JIRA; }
            @Override public int collect(DataSourceConfig c, SyncJobTracker.JobState js) { return 3; }
        };
        when(collectorRegistry.forType(DataSourceType.JIRA)).thenReturn(stubCollector);
        when(configRepository.save(cfg)).thenReturn(cfg);

        // Must not throw even with null jobState
        collectService.collectForDataSource(USER_ID, DS_ID, null);
    }
}
