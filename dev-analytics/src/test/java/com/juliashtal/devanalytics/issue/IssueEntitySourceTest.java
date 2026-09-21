package com.juliashtal.devanalytics.issue;

import com.juliashtal.devanalytics.issue.model.IssueEntity;
import com.juliashtal.devanalytics.issue.model.IssueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IssueEntitySourceTest {

    @Test
    void issueSource_enumValues() {
        assertThat(IssueSource.values())
            .containsExactlyInAnyOrder(IssueSource.GITHUB, IssueSource.JIRA);
    }

    @Test
    void githubIssue_carriesItsSource() {
        IssueEntity issue = new IssueEntity();
        issue.setSource(IssueSource.GITHUB);

        assertThat(issue.getSource()).isEqualTo(IssueSource.GITHUB);
    }

    @Test
    void jiraIssue_carriesItsSource() {
        IssueEntity issue = new IssueEntity();
        issue.setSource(IssueSource.JIRA);

        assertThat(issue.getSource()).isEqualTo(IssueSource.JIRA);
    }
}
