package com.juliashtal.devanalytics.github.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Acceptance tests for identity capture at ingest.
 *
 * <p>GitHub's commit JSON carries two notions of author: {@code commit.author}, the raw Git
 * trailer, and a top-level {@code author}, the account GitHub resolved from it. Both are captured,
 * and the null case matters equally — storing anything but null would invent an attribution.
 * HTTP is stubbed with WireMock, following {@code GitHubCommitIngestServiceTest}.</p>
 */
@ExtendWith(MockitoExtension.class)
@WireMockTest
class IdentityIngestMappingTest {

    @Mock GitRepositoryEntityRepository repoRepository;
    @Mock GitCommitEntityRepository commitRepository;
    @Mock GitHubPullRequestRepository prRepository;
    @Mock GitHubClientFactory clientFactory;

    GitHubCommitIngestService commitIngest;
    GitHubPullRequestCollector prCollector;
    String wmBaseUrl;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        wmBaseUrl = wm.getHttpBaseUrl();
        commitIngest = new GitHubCommitIngestService(repoRepository, commitRepository, clientFactory, new ObjectMapper());
        prCollector = new GitHubPullRequestCollector(repoRepository, prRepository, clientFactory, new ObjectMapper());
    }

    private GitRepositoryEntity repo() {
        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(1L);
        cfg.setBaseUrl(wmBaseUrl);

        GitRepositoryEntity r = new GitRepositoryEntity();
        r.setId(10L);
        r.setName("owner/test-repo");
        r.setDataSourceConfig(cfg);
        return r;
    }

    // ── commits ─────────────────────────────────────────────────────────────

    @Test
    void buildPendingEntity_commitWithResolvedAuthor_storesGithubIdAndLogin() {
        stubCommits("""
            [{
              "sha": "abc123",
              "commit": {"author": {"name": "Julia", "email": "49405289+julia@users.noreply.github.com",
                                    "date": "2026-03-02T10:00:00Z"}, "message": "msg"},
              "author": {"id": 49405289, "login": "julia-shtal"},
              "parents": []
            }]
            """);

        GitCommitEntity saved = ingestOneCommit();

        // The Git trailer address is preserved untouched -- ingestion keeps raw signal.
        assertThat(saved.getAuthorEmail()).isEqualTo("49405289+julia@users.noreply.github.com");
        // And the account GitHub resolved is captured alongside it, which is what makes this
        // commit attributable at all.
        assertThat(saved.getAuthorGithubId()).isEqualTo(49405289L);
        assertThat(saved.getAuthorGithubLogin()).isEqualTo("julia-shtal");
    }

    @Test
    void buildPendingEntity_commitWithNullAuthor_storesNullIdAndLogin() {
        stubCommits("""
            [{
              "sha": "def456",
              "commit": {"author": {"name": "Nobody", "email": "nobody@example.com",
                                    "date": "2026-03-02T10:00:00Z"}, "message": "msg"},
              "author": null,
              "parents": []
            }]
            """);

        GitCommitEntity saved = ingestOneCommit();

        // GitHub could not resolve the address to an account. Null is the honest answer; the
        // declared-address path is what covers this commit, if anyone declares that address.
        assertThat(saved.getAuthorGithubId()).isNull();
        assertThat(saved.getAuthorGithubLogin()).isNull();
        assertThat(saved.getAuthorEmail()).isEqualTo("nobody@example.com");
    }

    @Test
    void buildPendingEntity_commitWithAuthorObjectMissingId_storesNullId() {
        // Defensive: an author object without a numeric id must not be coerced to 0, which
        // would be a real account id as far as every downstream query is concerned.
        stubCommits("""
            [{
              "sha": "ghi789",
              "commit": {"author": {"name": "Odd", "email": "odd@example.com",
                                    "date": "2026-03-02T10:00:00Z"}, "message": "msg"},
              "author": {"login": "odd-one"},
              "parents": []
            }]
            """);

        GitCommitEntity saved = ingestOneCommit();

        assertThat(saved.getAuthorGithubId()).isNull();
    }

    // ── pull requests ───────────────────────────────────────────────────────

    @Test
    void mapPr_pullRequestWithUser_storesAuthorGithubIdBesideLogin() {
        GitRepositoryEntity repo = repo();
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("ghp_token");
        when(prRepository.findByRepository(any())).thenReturn(List.of());
        when(prRepository.saveAll(any())).thenAnswer(inv -> List.copyOf(inv.getArgument(0)));

        stubFor(get(urlPathMatching("/repos/owner/test-repo/pulls.*"))
                .willReturn(okJson("""
                    [{
                      "number": 42, "title": "A PR", "state": "closed", "merged_at": null,
                      "created_at": "2026-03-02T10:00:00Z", "updated_at": "2026-03-02T11:00:00Z",
                      "closed_at": null,
                      "user": {"id": 49405289, "login": "julia-shtal"}
                    }]
                    """)));

        prCollector.collectPullRequests(10L, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GitHubPullRequestEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(prRepository).saveAll(captor.capture());
        GitHubPullRequestEntity pr = captor.getValue().get(0);

        assertThat(pr.getAuthorGithubId()).isEqualTo(49405289L);
        // The login survives as a display value; attribution no longer depends on it.
        assertThat(pr.getAuthorLogin()).isEqualTo("julia-shtal");
    }

    @Test
    void mapPr_pullRequestWithNullUser_storesNullAuthorGithubId() {
        GitRepositoryEntity repo = repo();
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("ghp_token");
        when(prRepository.findByRepository(any())).thenReturn(List.of());
        when(prRepository.saveAll(any())).thenAnswer(inv -> List.copyOf(inv.getArgument(0)));

        stubFor(get(urlPathMatching("/repos/owner/test-repo/pulls.*"))
                .willReturn(okJson("""
                    [{
                      "number": 43, "title": "Ghost PR", "state": "closed", "merged_at": null,
                      "created_at": "2026-03-02T10:00:00Z", "updated_at": "2026-03-02T11:00:00Z",
                      "closed_at": null,
                      "user": null
                    }]
                    """)));

        prCollector.collectPullRequests(10L, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GitHubPullRequestEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(prRepository).saveAll(captor.capture());

        assertThat(captor.getValue().get(0).getAuthorGithubId()).isNull();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void stubCommits(String body) {
        stubFor(get(urlPathMatching("/repos/owner/test-repo/commits.*"))
                .willReturn(okJson(body)));
    }

    private GitCommitEntity ingestOneCommit() {
        GitRepositoryEntity repo = repo();
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("ghp_token");
        when(commitRepository.findHashesByRepositoryId(10L)).thenReturn(List.of());
        when(commitRepository.saveAll(any())).thenAnswer(inv -> List.copyOf(inv.getArgument(0)));

        commitIngest.ingestForRepository(10L, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GitCommitEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(commitRepository).saveAll(captor.capture());
        return captor.getValue().get(0);
    }
}
