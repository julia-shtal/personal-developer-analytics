package com.juliashtal.devanalytics.datasource.collect;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;

/**
 * Contract for a single data-source type's collection logic.
 * Implement one bean per {@link DataSourceType} and register it in the Spring context.
 * {@link SourceCollectorRegistry} picks it up automatically.
 */
public interface SourceCollector {

    /** Discriminator — must be unique across all registered implementations. */
    DataSourceType supports();

    /**
     * Performs the full collection run for the given data source configuration.
     *
     * @param cfg      the data-source configuration record
     * @param jobState live-progress handle; may be {@code null} in tests
     * @return total number of items (commits / PRs / issues) persisted
     */
    int collect(DataSourceConfig cfg, SyncJobTracker.JobState jobState);

    /**
     * How many tracker phases this source's job occupies (default 1).
     * Override when a source has multiple sequential phases (e.g. commits then PRs).
     */
    default int phaseCount(DataSourceConfig cfg) {
        return 1;
    }
}
