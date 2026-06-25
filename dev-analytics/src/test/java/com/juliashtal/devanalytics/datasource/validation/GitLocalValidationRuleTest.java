package com.juliashtal.devanalytics.datasource.validation;

import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitLocalValidationRuleTest {

    private final GitLocalValidationRule rule = new GitLocalValidationRule();

    @Test
    void supports_returnsGitLocal() {
        assertThat(rule.supports()).isEqualTo(DataSourceType.GIT_LOCAL);
    }

    @Test
    void validate_nullPath_throws() {
        assertThatThrownBy(() -> rule.validate(req(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path is required");
    }

    @Test
    void validate_blankPath_throws() {
        assertThatThrownBy(() -> rule.validate(req("   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path is required");
    }

    @Test
    void validate_nonexistentPath_throws() {
        assertThatThrownBy(() -> rule.validate(req("/no/such/path")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void validate_validDirectory_passes(@TempDir Path dir) {
        assertThatCode(() -> rule.validate(req(dir.toString()))).doesNotThrowAnyException();
    }

    private static CreateDataSourceRequest req(String path) {
        CreateDataSourceRequest r = new CreateDataSourceRequest();
        r.setType(DataSourceType.GIT_LOCAL);
        r.setPath(path);
        return r;
    }
}
