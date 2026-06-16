package com.juliashtal.devanalytics.github.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.exception.GitHubException;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@WireMockTest
class GitHubCommitIngestServiceTest {

    @Mock GitRepositoryEntityRepository repoRepository;
    @Mock GitCommitEntityRepository commitRepository;
    @Mock GitHubClientFactory clientFactory;

    GitHubCommitIngestService service;
    String wmBaseUrl;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        service = new GitHubCommitIngestService(repoRepository, commitRepository, clientFactory, new ObjectMapper());
        wmBaseUrl = wm.getHttpBaseUrl();
    }

    private GitRepositoryEntity repo(String lastFetched) {
        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(1L);
        cfg.setBaseUrl(wmBaseUrl);

        GitRepositoryEntity r = new GitRepositoryEntity();
        r.setId(10L);
        r.setName("owner/test-repo");
        r.setLastFetchedCommitHash(lastFetched);
        r.setDataSourceConfig(cfg);
        return r;
    }

    // ── repo not found ──────────────────────────────────────────────────────

    @Test
    void ingestForRepository_repoNotFound_throwsNoSuchElement() {
        when(repoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ingestForRepository(99L, null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Git repo not found");
    }

    // ── empty commit array ──────────────────────────────────────────────────

    @Test
    void ingestForRepository_emptyCommitArray_savesNothingAndUpdatesRepo() {
        GitRepositoryEntity repo = repo(null);
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("ghp_token");
        when(commitRepository.findHashesByRepositoryId(10L)).thenReturn(List.of());

        stubFor(get(urlPathMatching("/repos/owner/test-repo/commits.*"))
                .willReturn(aResponse().withStatus(200).withBody("[]")
                        .withHeader("Content-Type", "application/json")));

        GitHubCommitIngestService.IngestResult result = service.ingestForRepository(10L, null);

        assertThat(result.savedEntities()).isEmpty();
        verify(repoRepository).save(repo);
        verify(commitRepository, never()).saveAll(any());
    }

    // ── commit with no parents → parentHash null ───────────────────────────

    @Test
    void ingestForRepository_commitWithNoParents_parentHashIsNull() {
        GitRepositoryEntity repo = repo(null);
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("ghp_token");
        when(commitRepository.findHashesByRepositoryId(10L)).thenReturn(List.of());
        when(commitRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        String body = """
                [{"sha":"abc123","commit":{"author":{"name":"Alice","email":"alice@example.com",
                 "date":"2024-01-01T10:00:00Z"},"message":"root commit"},"parents":[]}]""";
        stubFor(get(urlPathMatching("/repos/owner/test-repo/commits.*"))
                .willReturn(aResponse().withStatus(200).withBody(body)
                        .withHeader("Content-Type", "application/json")));

        GitHubCommitIngestService.IngestResult result = service.ingestForRepository(10L, null);

        assertThat(result.savedEntities()).hasSize(1);
        GitCommitEntity saved = result.savedEntities().get(0);
        assertThat(saved.getHash()).isEqualTo("abc123");
        assertThat(saved.getParentHash()).isNull();
        assertThat(saved.getStatsStatus()).isEqualTo(StatsStatus.PENDING);
        assertThat(saved.getAuthorName()).isEqualTo("Alice");
        assertThat(saved.getAuthorEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getMessage()).isEqualTo("root commit");
        assertThat(repo.getLastFetchedCommitHash()).isEqualTo("abc123");
    }

    // ── commit with parent and null date → fallback to Instant.now() ───────

    @Test
    void ingestForRepository_commitWithParentAndNullDate_setsParentHashAndFallbackDate() {
        GitRepositoryEntity repo = repo(null);
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("ghp_token");
        when(commitRepository.findHashesByRepositoryId(10L)).thenReturn(List.of());
        when(commitRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        // "date" field absent → dateStr is null → uses Instant.now()
        String body = """
                [{"sha":"c2","commit":{"author":{"name":"Bob","email":"b@b.com"},"message":"fix"},
                  "parents":[{"sha":"c1"}]}]""";
        stubFor(get(urlPathMatching("/repos/owner/test-repo/commits.*"))
                .willReturn(aResponse().withStatus(200).withBody(body)
                        .withHeader("Content-Type", "application/json")));

        GitHubCommitIngestService.IngestResult result = service.ingestForRepository(10L, null);

        assertThat(result.savedEntities()).hasSize(1);
        GitCommitEntity entity = result.savedEntities().get(0);
        assertThat(entity.getParentHash()).isEqualTo("c1");
        assertThat(entity.getAuthorDate()).isNotNull();
    }

    // ── non-200 response → GitHubException ─────────────────────────────────

    @Test
    void ingestForRepository_nonOkResponse_throwsGitHubException() {
        GitRepositoryEntity repo = repo(null);
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("ghp_token");
        when(commitRepository.findHashesByRepositoryId(10L)).thenReturn(List.of());

        stubFor(get(urlPathMatching("/repos/owner/test-repo/commits.*"))
                .willReturn(aResponse().withStatus(404).withBody("not found")));

        assertThatThrownBy(() -> service.ingestForRepository(10L, null))
                .isInstanceOf(GitHubException.class)
                .hasMessageContaining("404");
    }

    // ── lastFetched hash found → stops at that commit ──────────────────────

    @Test
    void ingestForRepository_lastFetchedHashFound_stopsAtKnownCommit() {
        GitRepositoryEntity repo = repo("sha-old");
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("ghp_token");
        when(commitRepository.findHashesByRepositoryId(10L)).thenReturn(List.of());
        when(commitRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        String body = """
                [{"sha":"sha-new","commit":{"author":{"name":"A","email":"a@a.com",
                  "date":"2024-01-02T00:00:00Z"},"message":"new"},"parents":[]},
                 {"sha":"sha-old","commit":{"author":{"name":"B","email":"b@b.com",
                  "date":"2024-01-01T00:00:00Z"},"message":"known"},"parents":[]}]""";
        stubFor(get(urlPathMatching("/repos/owner/test-repo/commits.*"))
                .willReturn(aResponse().withStatus(200).withBody(body)
                        .withHeader("Content-Type", "application/json")));

        GitHubCommitIngestService.IngestResult result = service.ingestForRepository(10L, null);

        assertThat(result.savedEntities()).hasSize(1);
        assertThat(result.savedEntities().get(0).getHash()).isEqualTo("sha-new");
        assertThat(repo.getLastFetchedCommitHash()).isEqualTo("sha-new");
    }

    // ── existing hash skipped, jobState phaseProcessed tracked ─────────────

    @Test
    void ingestForRepository_existingHashSkipped_jobStateTracksAllIterated() {
        // Use non-null lastFetched to skip fetchTotalCommitCount.
        GitRepositoryEntity repo = repo("sha-never-matches");
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("ghp_token");
        when(commitRepository.findHashesByRepositoryId(10L)).thenReturn(List.of("sha-exists"));
        when(commitRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        String body = """
                [{"sha":"sha-new","commit":{"author":{"name":"A","email":"a@a.com",
                  "date":"2024-01-02T00:00:00Z"},"message":"new"},"parents":[]},
                 {"sha":"sha-exists","commit":{"author":{"name":"B","email":"b@b.com",
                  "date":"2024-01-01T00:00:00Z"},"message":"skip"},"parents":[]}]""";
        stubFor(get(urlPathMatching("/repos/owner/test-repo/commits.*"))
                .willReturn(aResponse().withStatus(200).withBody(body)
                        .withHeader("Content-Type", "application/json")));

        SyncJobTracker.JobState jobState = new SyncJobTracker.JobState();
        GitHubCommitIngestService.IngestResult result = service.ingestForRepository(10L, jobState);

        // sha-new is saved; sha-exists is in existingHashes so skipped (not added to newNodes).
        assertThat(result.savedEntities()).hasSize(1);
        assertThat(result.savedEntities().get(0).getHash()).isEqualTo("sha-new");
        // Both commits increment counters (increment happens before existingHashes check).
        assertThat(jobState.phaseProcessed.get()).isEqualTo(2);
        assertThat(jobState.totalProcessed.get()).isEqualTo(2);
    }

    // ── jobState + fetchTotalCommitCount via Link header ────────────────────

    @Test
    void ingestForRepository_withJobStateAndNoLastFetched_estimatesCountFromLinkHeader() {
        GitRepositoryEntity repo = repo(null);
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("ghp_token");
        when(commitRepository.findHashesByRepositoryId(10L)).thenReturn(List.of());
        when(commitRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        // per_page=1 request → Link header says page 42 = 42 total commits.
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/commits"))
                .withQueryParam("per_page", equalTo("1"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Link", "<http://x.com?page=42>; rel=\"last\"")
                        .withBody("")));

        String body = """
                [{"sha":"sha1","commit":{"author":{"name":"A","email":"a@a.com",
                  "date":"2024-01-01T00:00:00Z"},"message":"m"},"parents":[]}]""";
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/commits"))
                .withQueryParam("per_page", equalTo("100"))
                .willReturn(aResponse().withStatus(200).withBody(body)
                        .withHeader("Content-Type", "application/json")));

        SyncJobTracker.JobState jobState = new SyncJobTracker.JobState();
        GitHubCommitIngestService.IngestResult result = service.ingestForRepository(10L, jobState);

        assertThat(jobState.phaseTotal).isEqualTo(42);
        assertThat(result.savedEntities()).hasSize(1);
    }

    // ── multi-page pagination collects all commits ──────────────────────────

    @Test
    void ingestForRepository_multiplePages_collectsAllCommits() {
        GitRepositoryEntity repo = repo(null);
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("ghp_token");
        when(commitRepository.findHashesByRepositoryId(10L)).thenReturn(List.of());
        when(commitRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        String page1 = """
                [{"sha":"sha1","commit":{"author":{"name":"A","email":"a@a.com",
                  "date":"2024-01-02T00:00:00Z"},"message":"m1"},"parents":[]}]""";
        String page2 = """
                [{"sha":"sha2","commit":{"author":{"name":"B","email":"b@b.com",
                  "date":"2024-01-01T00:00:00Z"},"message":"m2"},"parents":[]}]""";

        // Page 1: has Link rel="next" header to trigger continuation.
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/commits"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(aResponse().withStatus(200).withBody(page1)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Link", "<" + wmBaseUrl
                                + "/repos/owner/test-repo/commits?per_page=100&page=2>; rel=\"next\"")));

        // Page 2: no Link header → stops after this page.
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/commits"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(aResponse().withStatus(200).withBody(page2)
                        .withHeader("Content-Type", "application/json")));

        GitHubCommitIngestService.IngestResult result = service.ingestForRepository(10L, null);

        assertThat(result.savedEntities()).hasSize(2);
    }

    // ── rate limit retry: 429 → Retry-After: 0 → 200 ──────────────────────

    @Test
    void ingestForRepository_rateLimitedOnFirstRequest_retriesAndSucceeds() {
        GitRepositoryEntity repo = repo(null);
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("ghp_token");
        when(commitRepository.findHashesByRepositoryId(10L)).thenReturn(List.of());

        stubFor(get(urlPathMatching("/repos/owner/test-repo/commits.*"))
                .inScenario("rate-limit")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(429)
                        .withHeader("Retry-After", "0"))
                .willSetStateTo("retried"));

        stubFor(get(urlPathMatching("/repos/owner/test-repo/commits.*"))
                .inScenario("rate-limit")
                .whenScenarioStateIs("retried")
                .willReturn(aResponse().withStatus(200).withBody("[]")
                        .withHeader("Content-Type", "application/json")));

        GitHubCommitIngestService.IngestResult result = service.ingestForRepository(10L, null);

        assertThat(result.savedEntities()).isEmpty();
    }

    // ── blank token → no Authorization header sent ──────────────────────────

    @Test
    void ingestForRepository_blankToken_sendsRequestWithoutAuthHeader() {
        GitRepositoryEntity repo = repo(null);
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("   ");
        when(commitRepository.findHashesByRepositoryId(10L)).thenReturn(List.of());

        stubFor(get(urlPathMatching("/repos/owner/test-repo/commits.*"))
                .willReturn(aResponse().withStatus(200).withBody("[]")
                        .withHeader("Content-Type", "application/json")));

        service.ingestForRepository(10L, null);

        verify(getRequestedFor(urlPathMatching("/repos/owner/test-repo/commits.*"))
                .withoutHeader("Authorization"));
    }
}
