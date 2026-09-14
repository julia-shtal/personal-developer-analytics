package com.juliashtal.devanalytics.helper;

import org.junit.jupiter.api.Test;

import static com.juliashtal.devanalytics.helper.ParsingHelper.resolveApiBase;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that base-URL resolution routes github.com to the public API host and leaves every
 * GitHub Enterprise host on its own endpoint.
 */
class ParsingHelperTest {

    @Test
    void resolveApiBase_nullUrl_returnsPublicApiHost() {
        assertThat(resolveApiBase(null)).isEqualTo("https://api.github.com");
    }

    @Test
    void resolveApiBase_blankUrl_returnsPublicApiHost() {
        assertThat(resolveApiBase("   ")).isEqualTo("https://api.github.com");
    }

    @Test
    void resolveApiBase_githubComHost_returnsPublicApiHost() {
        assertThat(resolveApiBase("https://github.com")).isEqualTo("https://api.github.com");
    }

    @Test
    void resolveApiBase_githubComHostWithTrailingSlash_returnsPublicApiHost() {
        assertThat(resolveApiBase("https://github.com/")).isEqualTo("https://api.github.com");
    }

    @Test
    void resolveApiBase_githubComOrgPath_returnsPublicApiHost() {
        assertThat(resolveApiBase("https://github.com/myorg")).isEqualTo("https://api.github.com");
    }

    @Test
    void resolveApiBase_mixedCaseGithubComHost_returnsPublicApiHost() {
        assertThat(resolveApiBase("HTTPS://GitHub.com")).isEqualTo("https://api.github.com");
    }

    @Test
    void resolveApiBase_enterpriseUrl_returnsUrlUnchanged() {
        assertThat(resolveApiBase("https://ghe.mycompany.com/api/v3"))
                .isEqualTo("https://ghe.mycompany.com/api/v3");
    }

    @Test
    void resolveApiBase_enterpriseUrlWithTrailingSlashes_returnsTrimmedUrl() {
        assertThat(resolveApiBase("https://ghe.mycompany.com/api/v3//"))
                .isEqualTo("https://ghe.mycompany.com/api/v3");
    }

    @Test
    void resolveApiBase_hostPrefixedByGithubCom_returnsUrlUnchanged() {
        assertThat(resolveApiBase("https://github.company.com/api/v3"))
                .isEqualTo("https://github.company.com/api/v3");
    }
}
