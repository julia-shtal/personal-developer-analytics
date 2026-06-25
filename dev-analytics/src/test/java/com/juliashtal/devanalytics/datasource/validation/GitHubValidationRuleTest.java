package com.juliashtal.devanalytics.datasource.validation;

import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubValidationRuleTest {

    private final GitHubValidationRule rule = new GitHubValidationRule();

    @Test
    void supports_returnsGitHub() {
        assertThat(rule.supports()).isEqualTo(DataSourceType.GITHUB);
    }

    @Test
    void validate_nullBaseUrl_throws() {
        assertThatThrownBy(() -> rule.validate(req(null, "token")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseUrl is required");
    }

    @Test
    void validate_malformedBaseUrl_throws() {
        assertThatThrownBy(() -> rule.validate(req("not-a-url", "token")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid baseUrl");
    }

    @Test
    void validate_nullToken_throws() {
        assertThatThrownBy(() -> rule.validate(req("https://api.github.com", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiToken is required");
    }

    @Test
    void validate_valid_passes() {
        assertThatCode(() -> rule.validate(req("https://api.github.com", "ghp_token")))
                .doesNotThrowAnyException();
    }

    private static CreateDataSourceRequest req(String baseUrl, String token) {
        CreateDataSourceRequest r = new CreateDataSourceRequest();
        r.setType(DataSourceType.GITHUB);
        r.setBaseUrl(baseUrl);
        r.setApiToken(token);
        return r;
    }
}
