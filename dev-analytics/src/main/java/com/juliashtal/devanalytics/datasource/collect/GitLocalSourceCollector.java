package com.juliashtal.devanalytics.datasource.collect;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.service.GitLocalCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link SourceCollector} adapter for {@link DataSourceType#GIT_LOCAL}.
 * Wraps the per-repo loop that was previously in {@code DataSourceCollectService}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GitLocalSourceCollector implements SourceCollector {

    private final GitRepositoryEntityRepository gitRepoRepository;
    private final GitLocalCollector gitLocalCollector;
    private final SyncJobTracker tracker;

    @Override
    public DataSourceType supports() {
        return DataSourceType.GIT_LOCAL;
    }

    @Override
    public int collect(DataSourceConfig cfg, SyncJobTracker.JobState jobState) {
        int total = 0;
        for (var repo : gitRepoRepository.findAllByDataSourceConfig(cfg)) {
            try {
                if (jobState != null) tracker.setPhase(jobState, "commits", -1);
                int n = gitLocalCollector.collectForRepository(repo.getId(), jobState);
                total += n;
            } catch (Exception e) {
                log.warn("Collection failed for repo {}: {}", repo.getId(), e.getMessage());
            }
        }
        return total;
    }
}
