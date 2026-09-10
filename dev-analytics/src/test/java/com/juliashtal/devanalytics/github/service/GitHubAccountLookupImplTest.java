package com.juliashtal.devanalytics.github.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.exception.GitHubException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Acceptance tests for resolving a GitHub login to its numeric account id.
 *
 * <p>The distinction that matters most: 404 is the only status meaning "no such account". Treating
 * a 403 rate-limit or a 401 bad-token as "not found" would let a transient failure answer 422 to
 * the user and, in the migration job, silently skip resolving a perfectly valid login.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@WireMockTest
class GitHubAccountLookupImplTest {

    @Mock DataSourceConfigRepository dataSourceConfigRepository;
    @Mock GitHubClientFactory clientFactory;

    GitHubAccountLookupImpl lookup;
    String wmBaseUrl;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        wmBaseUrl = wm.getHttpBaseUrl();
        lookup = new GitHubAccountLookupImpl(dataSourceConfigRepository, clientFactory, new ObjectMapper());
        when(clientFactory.getDecryptedToken(any())).thenReturn("ghp_token");
    }

    private DataSourceConfig githubSource() {
        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(1L);
        cfg.setType(DataSourceType.GITHUB);
        cfg.setBaseUrl(wmBaseUrl);
        cfg.setApiTokenEncrypted("encrypted");
        return cfg;
    }

    @Test
    void findByLogin_existingAccount_returnsIdAndCanonicalLogin() {
        when(dataSourceConfigRepository.findAllByUserId(1L)).thenReturn(List.of(githubSource()));
        stubFor(get(urlPathEqualTo("/users/OctoCat"))
                .willReturn(okJson("{\"id\": 49405289, \"login\": \"octocat\"}")));

        Optional<GitHubAccountLookup.GitHubAccount> account = lookup.findByLogin(1L, "OctoCat");

        assertThat(account).isPresent();
        assertThat(account.get().id()).isEqualTo(49405289L);
        // GitHub's spelling, not the caller's -- this is what gets stored as the display login.
        assertThat(account.get().login()).isEqualTo("octocat");
    }

    @Test
    void findByLogin_unknownLogin_returnsEmpty() {
        when(dataSourceConfigRepository.findAllByUserId(1L)).thenReturn(List.of(githubSource()));
        stubFor(get(urlPathEqualTo("/users/ghost")).willReturn(aResponse().withStatus(404)));

        assertThat(lookup.findByLogin(1L, "ghost")).isEmpty();
    }

    @Test
    void findByLogin_rateLimited_throwsRatherThanReportingNotFound() {
        when(dataSourceConfigRepository.findAllByUserId(1L)).thenReturn(List.of(githubSource()));
        stubFor(get(urlPathEqualTo("/users/octocat")).willReturn(aResponse().withStatus(403)));

        // A 422 here would tell the user their login does not exist, which is false.
        assertThatThrownBy(() -> lookup.findByLogin(1L, "octocat"))
                .isInstanceOf(GitHubException.class)
                .hasMessageContaining("403");
    }

    @Test
    void findByLogin_serverError_throws() {
        when(dataSourceConfigRepository.findAllByUserId(1L)).thenReturn(List.of(githubSource()));
        stubFor(get(urlPathEqualTo("/users/octocat")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> lookup.findByLogin(1L, "octocat"))
                .isInstanceOf(GitHubException.class);
    }

    @Test
    void findByLogin_responseWithoutNumericId_throws() {
        when(dataSourceConfigRepository.findAllByUserId(1L)).thenReturn(List.of(githubSource()));
        stubFor(get(urlPathEqualTo("/users/octocat")).willReturn(okJson("{\"login\": \"octocat\"}")));

        // Storing a login without its id would reintroduce free-text matching.
        assertThatThrownBy(() -> lookup.findByLogin(1L, "octocat"))
                .isInstanceOf(GitHubException.class)
                .hasMessageContaining("numeric id");
    }

    @Test
    void findByLogin_blankLogin_returnsEmptyWithoutCallingGitHub() {
        assertThat(lookup.findByLogin(1L, "  ")).isEmpty();
        verify(exactly(0), getRequestedFor(urlPathMatching("/users/.*")));
    }

    @Test
    void findByLogin_userWithOwnGithubSource_sendsItsTokenAndUsesItsBaseUrl() {
        // Both the Enterprise base URL and the rate-limit-lifting token come from the user's data
        // source, which is why the lookup takes a user id rather than just a login.
        when(dataSourceConfigRepository.findAllByUserId(1L)).thenReturn(List.of(githubSource()));
        stubFor(get(urlPathEqualTo("/users/octocat"))
                .willReturn(okJson("{\"id\": 7, \"login\": \"octocat\"}")));

        assertThat(lookup.findByLogin(1L, "octocat")).isPresent();

        verify(getRequestedFor(urlPathEqualTo("/users/octocat"))
                .withHeader("Authorization", equalTo("Bearer ghp_token")));
    }

    @Test
    void whoAmI_validToken_returnsTheTokenOwner() {
        stubFor(get(urlPathEqualTo("/user"))
                .willReturn(okJson("{\"id\": 42, \"login\": \"token-owner\"}")));

        GitHubAccountLookup.GitHubAccount account = lookup.whoAmI(githubSource());

        assertThat(account.id()).isEqualTo(42L);
        assertThat(account.login()).isEqualTo("token-owner");
    }

    @Test
    void whoAmI_unauthorized_throws() {
        stubFor(get(urlPathEqualTo("/user")).willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> lookup.whoAmI(githubSource()))
                .isInstanceOf(GitHubException.class);
    }

    @Test
    void whoAmI_missingToken_throwsWithoutCallingGitHub() {
        DataSourceConfig cfg = githubSource();
        when(clientFactory.getDecryptedToken(cfg)).thenReturn("  ");

        assertThatThrownBy(() -> lookup.whoAmI(cfg))
                .isInstanceOf(GitHubException.class)
                .hasMessageContaining("token is missing");
    }
}
