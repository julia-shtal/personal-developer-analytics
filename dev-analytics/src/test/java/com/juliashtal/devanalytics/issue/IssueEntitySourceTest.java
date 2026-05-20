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
    void githubIssue_sourceAndContext() {
        IssueEntity issue = new IssueEntity();
        issue.setSource(IssueSource.GITHUB);
        issue.setSourceContext("owner/repo");

        assertThat(issue.getSource()).isEqualTo(IssueSource.GITHUB);
        assertThat(issue.getSourceContext()).isEqualTo("owner/repo");
    }

    @Test
    void jiraIssue_sourceAndContext() {
        IssueEntity issue = new IssueEntity();
        issue.setSource(IssueSource.JIRA);
        issue.setSourceContext("PDA");

        assertThat(issue.getSource()).isEqualTo(IssueSource.JIRA);
        assertThat(issue.getSourceContext()).isEqualTo("PDA");
    }
}
