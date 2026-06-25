package com.juliashtal.devanalytics.datasource.service;

import com.juliashtal.devanalytics.datasource.collect.SourceCollectorRegistry;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataSourceCollectService {

    private final DataSourceConfigRepository configRepository;
    private final DataSourceService dataSourceService;
    private final SourceCollectorRegistry collectorRegistry;
    private final SyncJobTracker tracker;

    /**
     * Triggers collection for all repositories under the given data source.
     * Delegates to the registered {@link com.juliashtal.devanalytics.datasource.collect.SourceCollector}
     * for the data-source type; no switch over type remains here.
     *
     * @param jobState live-progress handle; may be null in tests
     * @return human-readable summary string
     */
    public String collectForDataSource(Long userId, Long dataSourceId, SyncJobTracker.JobState jobState) {
        DataSourceConfig cfg = dataSourceService.getForUser(userId, dataSourceId);
        log.info("Collection started: dataSourceId={}, type={}, userId={}", dataSourceId, cfg.getType(), userId);

        if (jobState != null) {
            jobState.totalPhases = collectorRegistry.forType(cfg.getType()).phaseCount(cfg);
        }

        int total = collectorRegistry.forType(cfg.getType()).collect(cfg, jobState);

        cfg.setLastSuccessSync(Instant.now());
        configRepository.save(cfg);

        String summary = total == 0 ? "Nothing to collect (no repos registered)" : total + " items collected";
        log.info("Collection finished: dataSourceId={}, total={}", dataSourceId, total);
        return summary;
    }
}
