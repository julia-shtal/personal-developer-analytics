package com.juliashtal.devanalytics.datasource.validation;

import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JiraValidationRuleTest {

    private final JiraValidationRule rule = new JiraValidationRule();

    @Test
    void supports_returnsJira() {
        assertThat(rule.supports()).isEqualTo(DataSourceType.JIRA);
    }

    @Test
    void validate_nullBaseUrl_throws() {
        assertThatThrownBy(() -> rule.validate(req(null, "user:token")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseUrl is required");
    }

    @Test
    void validate_valid_passes() {
        assertThatCode(() -> rule.validate(req("https://company.atlassian.net", "user:token")))
                .doesNotThrowAnyException();
    }

    private static CreateDataSourceRequest req(String baseUrl, String token) {
        CreateDataSourceRequest r = new CreateDataSourceRequest();
        r.setType(DataSourceType.JIRA);
        r.setBaseUrl(baseUrl);
        r.setApiToken(token);
        return r;
    }
}
