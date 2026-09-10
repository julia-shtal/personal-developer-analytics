package com.juliashtal.devanalytics.jira.service;

import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.issue.model.IssueEntity;
import com.juliashtal.devanalytics.issue.model.IssueSource;
import com.juliashtal.devanalytics.issue.model.JiraSearchResponse;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.repository.JiraProjectRepoMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Acceptance test for Jira issue storage.
 *
 * <p>Only display names used to be stored, which made Jira issues impossible to attribute after
 * collection: a display name is neither unique nor stable. Both accountIds must land on the row,
 * because {@code DAILY_ISSUES_CREATED} follows the reporter and {@code DAILY_ISSUES_CLOSED} and
 * {@code ISSUE_LEAD_TIME_HOURS_MEDIAN} follow the assignee.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JiraIssueUpsertTest {

    @Mock IssueRepository issueRepository;
    @Mock JiraProjectRepoMappingRepository jiraProjectRepoMappingRepository;

    @InjectMocks JiraCollector collector;

    private JiraProjectEntity project;

    @BeforeEach
    void setUp() {
        project = new JiraProjectEntity();
        project.setId(5L);
        project.setProjectKey("PDA");

        when(issueRepository.findByJiraProjectAndSourceIssueKey(any(), any())).thenReturn(Optional.empty());
        when(jiraProjectRepoMappingRepository.findAllByJiraProject(any())).thenReturn(List.of());
    }

    @Test
    void upsertJiraIssue_issueWithReporterAndAssignee_storesBothAccountIds() {
        collector.upsertJiraIssue(project, issue("PDA-1", "acct-reporter", "acct-assignee"));

        IssueEntity saved = captureSaved();
        assertThat(saved.getReporterAccountId()).isEqualTo("acct-reporter");
        assertThat(saved.getAssigneeAccountId()).isEqualTo("acct-assignee");
        assertThat(saved.getSource()).isEqualTo(IssueSource.JIRA);
    }

    @Test
    void upsertJiraIssue_issueWithReporterAndAssignee_keepsDisplayNames() {
        collector.upsertJiraIssue(project, issue("PDA-2", "acct-reporter", "acct-assignee"));

        IssueEntity saved = captureSaved();
        // The accountIds are for matching; the names still drive the UI.
        assertThat(saved.getCreator()).isEqualTo("Reporter Name");
        assertThat(saved.getAssignee()).isEqualTo("Assignee Name");
    }

    @Test
    void upsertJiraIssue_unassignedIssue_storesNullAssigneeAccountId() {
        JiraSearchResponse.JiraIssue ji = issue("PDA-3", "acct-reporter", null);
        ji.getFields().setAssignee(null);

        collector.upsertJiraIssue(project, ji);

        IssueEntity saved = captureSaved();
        assertThat(saved.getAssigneeAccountId()).isNull();
        assertThat(saved.getReporterAccountId()).isEqualTo("acct-reporter");
    }

    private IssueEntity captureSaved() {
        ArgumentCaptor<IssueEntity> captor = ArgumentCaptor.forClass(IssueEntity.class);
        verify(issueRepository).save(captor.capture());
        return captor.getValue();
    }

    private JiraSearchResponse.JiraIssue issue(String key, String reporterAccount, String assigneeAccount) {
        JiraSearchResponse.JiraUser reporter = new JiraSearchResponse.JiraUser();
        reporter.setAccountId(reporterAccount);
        reporter.setDisplayName("Reporter Name");

        JiraSearchResponse.JiraUser assignee = new JiraSearchResponse.JiraUser();
        assignee.setAccountId(assigneeAccount);
        assignee.setDisplayName("Assignee Name");

        JiraSearchResponse.Fields fields = new JiraSearchResponse.Fields();
        fields.setSummary("An issue");
        fields.setReporter(reporter);
        fields.setAssignee(assignee);

        JiraSearchResponse.JiraIssue ji = new JiraSearchResponse.JiraIssue();
        ji.setKey(key);
        ji.setFields(fields);
        return ji;
    }
}
