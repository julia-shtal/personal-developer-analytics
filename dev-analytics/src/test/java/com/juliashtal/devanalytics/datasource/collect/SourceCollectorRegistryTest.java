package com.juliashtal.devanalytics.datasource.collect;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceCollectorRegistryTest {

    private static SourceCollector stubFor(DataSourceType type) {
        return new SourceCollector() {
            @Override public DataSourceType supports() { return type; }
            @Override public int collect(DataSourceConfig cfg, SyncJobTracker.JobState js) { return 0; }
        };
    }

    @Test
    void forType_knownType_returnsCollector() {
        SourceCollectorRegistry registry = new SourceCollectorRegistry(
                List.of(stubFor(DataSourceType.GIT_LOCAL),
                        stubFor(DataSourceType.GITHUB),
                        stubFor(DataSourceType.JIRA)));

        SourceCollector result = registry.forType(DataSourceType.GIT_LOCAL);

        assertThat(result.supports()).isEqualTo(DataSourceType.GIT_LOCAL);
    }

    @Test
    void forType_unknownType_throwsIllegalArgumentException() {
        SourceCollectorRegistry registry = new SourceCollectorRegistry(
                List.of(stubFor(DataSourceType.GIT_LOCAL)));

        assertThatThrownBy(() -> registry.forType(DataSourceType.GITHUB))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GITHUB");
    }

    @Test
    void constructor_duplicateSupports_throwsIllegalStateException() {
        assertThatThrownBy(() -> new SourceCollectorRegistry(
                List.of(stubFor(DataSourceType.GIT_LOCAL),
                        stubFor(DataSourceType.GIT_LOCAL))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GIT_LOCAL");
    }
}
