package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.service.DataSourceCollectService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceTypeTest {

    @Test
    void enum_hasExactlyThreeValues_githubIssuesRemoved() {
        assertThat(DataSourceType.values())
                .containsExactlyInAnyOrder(
                        DataSourceType.GIT_LOCAL,
                        DataSourceType.GITHUB,
                        DataSourceType.JIRA
                );
    }

    @Test
    void enum_doesNotContain_githubIssues() {
        for (DataSourceType type : DataSourceType.values()) {
            assertThat(type.name()).isNotEqualTo("GITHUB_ISSUES");
        }
    }
}