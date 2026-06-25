package com.juliashtal.devanalytics.datasource.collect;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.jira.service.JiraCollector;
import com.juliashtal.devanalytics.jira.service.JiraProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link SourceCollector} adapter for {@link DataSourceType#JIRA}.
 * Iterates tracked Jira projects and delegates to {@link JiraCollector}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JiraSourceCollector implements SourceCollector {

    private final JiraProjectService jiraProjectService;
    private final JiraCollector jiraCollector;
    private final SyncJobTracker tracker;

    @Override
    public DataSourceType supports() {
        return DataSourceType.JIRA;
    }

    @Override
    public int collect(DataSourceConfig cfg, SyncJobTracker.JobState jobState) {
        int total = 0;
        var jiraProjects = jiraProjectService.listTrackedProjects(cfg);
        if (jiraProjects.isEmpty()) {
            log.warn("JIRA datasource {} has no tracked projects — nothing to collect", cfg.getId());
        } else {
            for (var project : jiraProjects) {
                try {
                    if (jobState != null) tracker.setPhase(jobState, "jira issues", -1);
                    int n = jiraCollector.collectIssues(project);
                    total += n;
                } catch (Exception e) {
                    log.warn("Jira collection failed for project {}: {}", project.getProjectKey(), e.getMessage());
                }
            }
        }
        return total;
    }
}
