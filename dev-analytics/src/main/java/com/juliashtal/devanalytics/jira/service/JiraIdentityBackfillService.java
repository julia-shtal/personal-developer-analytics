package com.juliashtal.devanalytics.jira.service;

import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.repository.JiraProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * {@link JiraIdentityBackfill} by re-running the ordinary collection over every project.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JiraIdentityBackfillService implements JiraIdentityBackfill {

    private final JiraProjectRepository jiraProjectRepository;
    private final JiraCollector jiraCollector;

    @Override
    public int recollectAllProjects() {
        int total = 0;
        for (JiraProjectEntity project : jiraProjectRepository.findAll()) {
            try {
                total += jiraCollector.collectIssues(project);
            } catch (RuntimeException e) {
                // One unreachable Jira instance must not stop the others. The issues keep their
                // null accountIds and simply match nobody until the next run.
                log.error("Jira identity backfill failed for projectId={}", project.getId(), e);
            }
        }
        log.info("Jira identity backfill: {} issues re-collected", total);
        return total;
    }
}
