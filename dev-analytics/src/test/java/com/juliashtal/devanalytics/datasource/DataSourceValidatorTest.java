package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;
import com.juliashtal.devanalytics.datasource.service.DataSourceValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class DataSourceValidatorTest {

    private final DataSourceValidator validator = new DataSourceValidator();

    // ── GIT_LOCAL: mirrors chk_gitlocal_path ─────────────────────────────────

    @Test
    void gitLocal_nullPath_throws() {
        assertThatThrownBy(() -> validator.validateCreate(gitLocalReq(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path is required");
    }

    @Test
    void gitLocal_blankPath_throws() {
        assertThatThrownBy(() -> validator.validateCreate(gitLocalReq("   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path is required");
    }

    @Test
    void gitLocal_nonexistentPath_throws() {
        assertThatThrownBy(() -> validator.validateCreate(gitLocalReq("/no/such/path")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void gitLocal_validPath_passes(@TempDir Path dir) {
        assertThatCode(() -> validator.validateCreate(gitLocalReq(dir.toString())))
                .doesNotThrowAnyException();
    }

    // ── GITHUB / JIRA: mirrors chk_remote_baseurl ────────────────────────────

    @Test
    void github_nullBaseUrl_throws() {
        assertThatThrownBy(() -> validator.validateCreate(httpReq(DataSourceType.GITHUB, null, "token")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseUrl is required");
    }

    @Test
    void github_malformedBaseUrl_throws() {
        assertThatThrownBy(() -> validator.validateCreate(httpReq(DataSourceType.GITHUB, "not-a-url", "token")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid baseUrl");
    }

    @Test
    void github_nullToken_throws() {
        assertThatThrownBy(() -> validator.validateCreate(httpReq(DataSourceType.GITHUB, "https://api.github.com", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiToken is required");
    }

    @Test
    void jira_nullBaseUrl_throws() {
        assertThatThrownBy(() -> validator.validateCreate(httpReq(DataSourceType.JIRA, null, "user:token")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseUrl is required");
    }

    @Test
    void github_valid_passes() {
        assertThatCode(() -> validator.validateCreate(
                httpReq(DataSourceType.GITHUB, "https://api.github.com", "ghp_token")))
                .doesNotThrowAnyException();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static CreateDataSourceRequest gitLocalReq(String path) {
        CreateDataSourceRequest r = new CreateDataSourceRequest();
        r.setType(DataSourceType.GIT_LOCAL);
        r.setName("local");
        r.setPath(path);
        return r;
    }

    private static CreateDataSourceRequest httpReq(DataSourceType type, String baseUrl, String token) {
        CreateDataSourceRequest r = new CreateDataSourceRequest();
        r.setType(type);
        r.setName("remote");
        r.setBaseUrl(baseUrl);
        r.setApiToken(token);
        return r;
    }
}
