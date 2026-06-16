package com.juliashtal.devanalytics.github.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.exception.GitHubException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
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
class GitHubPullRequestCollectorTest {

    @Mock GitRepositoryEntityRepository repoRepository;
    @Mock GitHubPullRequestRepository prRepository;
    @Mock GitHubClientFactory clientFactory;

    GitHubPullRequestCollector service;
    String wmBaseUrl;
    DataSourceConfig cfg;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        service = new GitHubPullRequestCollector(repoRepository, prRepository, clientFactory, new ObjectMapper());
        wmBaseUrl = wm.getHttpBaseUrl();
        cfg = new DataSourceConfig();
        cfg.setId(1L);
        cfg.setBaseUrl(wmBaseUrl);
    }

    private GitRepositoryEntity repo() {
        GitRepositoryEntity r = new GitRepositoryEntity();
        r.setId(10L);
        r.setName("owner/test-repo");
        r.setRepoFullName("owner/test-repo");
        r.setDataSourceConfig(cfg);
        return r;
    }

    // ── repo not found ──────────────────────────────────────────────────────

    @Test
    void collectPullRequests_repoNotFound_throwsNoSuchElement() {
        when(repoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.collectPullRequests(99L, null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Git repo not found");
    }

    // ── empty PR list ───────────────────────────────────────────────────────

    @Test
    void collectPullRequests_emptyList_returnsEmptyResult() {
        GitRepositoryEntity repo = repo();
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("token");
        when(prRepository.findByRepository(repo)).thenReturn(List.of());

        stubFor(get(urlPathMatching("/repos/owner/test-repo/pulls.*"))
                .willReturn(aResponse().withStatus(200).withBody("[]")
                        .withHeader("Content-Type", "application/json")));

        GitHubPullRequestCollector.IngestResult result = service.collectPullRequests(10L, null);

        assertThat(result.savedEntities()).isEmpty();
        verify(repoRepository).save(repo);
    }

    // ── new PR mapped correctly and marked PENDING ──────────────────────────

    @Test
    void collectPullRequests_newPr_savedAsPendingWithFields() {
        GitRepositoryEntity repo = repo();
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("token");
        when(prRepository.findByRepository(repo)).thenReturn(List.of());
        when(prRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        String body = """
                [{"number":42,"title":"My PR","state":"open",
                  "user":{"login":"alice"},
                  "created_at":"2024-01-01T10:00:00Z",
                  "updated_at":"2024-01-02T10:00:00Z",
                  "merged_at":null,"closed_at":null,
                  "comments":2,"review_comments":1}]""";
        stubFor(get(urlPathMatching("/repos/owner/test-repo/pulls.*"))
                .willReturn(aResponse().withStatus(200).withBody(body)
                        .withHeader("Content-Type", "application/json")));

        GitHubPullRequestCollector.IngestResult result = service.collectPullRequests(10L, null);

        assertThat(result.savedEntities()).hasSize(1);
        GitHubPullRequestEntity pr = result.savedEntities().get(0);
        assertThat(pr.getNumber()).isEqualTo(42);
        assertThat(pr.getTitle()).isEqualTo("My PR");
        assertThat(pr.getAuthorLogin()).isEqualTo("alice");
        assertThat(pr.getStatsStatus()).isEqualTo(StatsStatus.PENDING);
        assertThat(pr.getStatsAttempts()).isEqualTo(0);
        assertThat(pr.getLeadTimeHours()).isNull(); // not merged
        assertThat(pr.getAdditions()).isEqualTo(0);
    }

    // ── merged PR → lead time calculated ───────────────────────────────────

    @Test
    void collectPullRequests_mergedPr_calculatesLeadTimeHours() {
        GitRepositoryEntity repo = repo();
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("token");
        when(prRepository.findByRepository(repo)).thenReturn(List.of());
        when(prRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        String body = """
                [{"number":1,"title":"Merged","state":"closed",
                  "user":{"login":"bob"},
                  "created_at":"2024-01-01T00:00:00Z",
                  "updated_at":"2024-01-03T00:00:00Z",
                  "merged_at":"2024-01-03T00:00:00Z","closed_at":"2024-01-03T00:00:00Z",
                  "comments":0,"review_comments":0}]""";
        stubFor(get(urlPathMatching("/repos/owner/test-repo/pulls.*"))
                .willReturn(aResponse().withStatus(200).withBody(body)
                        .withHeader("Content-Type", "application/json")));

        GitHubPullRequestCollector.IngestResult result = service.collectPullRequests(10L, null);

        GitHubPullRequestEntity pr = result.savedEntities().get(0);
        assertThat(pr.getLeadTimeHours()).isEqualTo(48L);
        assertThat(pr.isMerged()).isTrue();
    }

    // ── unchanged PR (same updatedAt) → skipped ────────────────────────────

    @Test
    void collectPullRequests_unchangedPr_skipsAndDoesNotSave() {
        GitRepositoryEntity repo = repo();
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("token");

        GitHubPullRequestEntity existing = new GitHubPullRequestEntity();
        existing.setId(100L);
        existing.setNumber(1);
        existing.setUpdatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        when(prRepository.findByRepository(repo)).thenReturn(List.of(existing));

        String body = """
                [{"number":1,"title":"Old PR","state":"closed",
                  "user":{"login":"alice"},
                  "created_at":"2024-01-01T00:00:00Z",
                  "updated_at":"2024-01-01T00:00:00Z",
                  "merged_at":null,"closed_at":null,
                  "comments":0,"review_comments":0}]""";
        stubFor(get(urlPathMatching("/repos/owner/test-repo/pulls.*"))
                .willReturn(aResponse().withStatus(200).withBody(body)
                        .withHeader("Content-Type", "application/json")));

        GitHubPullRequestCollector.IngestResult result = service.collectPullRequests(10L, null);

        assertThat(result.savedEntities()).isEmpty();
        verify(prRepository, never()).saveAll(any());
    }

    // ── updated open PR → stats reset to PENDING ───────────────────────────

    @Test
    void collectPullRequests_updatedOpenPr_resetsStatsToPending() {
        GitRepositoryEntity repo = repo();
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("token");

        GitHubPullRequestEntity existing = new GitHubPullRequestEntity();
        existing.setId(200L);
        existing.setNumber(5);
        existing.setUpdatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        existing.setMerged(false);
        existing.setStatsStatus(StatsStatus.COMPLETE);
        existing.setStatsAttempts(2);
        when(prRepository.findByRepository(repo)).thenReturn(List.of(existing));
        when(prRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        String body = """
                [{"number":5,"title":"Updated","state":"open",
                  "user":{"login":"carol"},
                  "created_at":"2024-01-01T00:00:00Z",
                  "updated_at":"2024-01-02T00:00:00Z",
                  "merged_at":null,"closed_at":null,
                  "comments":0,"review_comments":0}]""";
        stubFor(get(urlPathMatching("/repos/owner/test-repo/pulls.*"))
                .willReturn(aResponse().withStatus(200).withBody(body)
                        .withHeader("Content-Type", "application/json")));

        GitHubPullRequestCollector.IngestResult result = service.collectPullRequests(10L, null);

        assertThat(result.savedEntities()).hasSize(1);
        GitHubPullRequestEntity pr = result.savedEntities().get(0);
        assertThat(pr.getStatsStatus()).isEqualTo(StatsStatus.PENDING);
        assertThat(pr.getStatsAttempts()).isEqualTo(0);
    }

    // ── merged COMPLETE PR → stats preserved ───────────────────────────────

    @Test
    void collectPullRequests_updatedMergedCompletePr_preservesStats() {
        GitRepositoryEntity repo = repo();
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("token");

        GitHubPullRequestEntity existing = new GitHubPullRequestEntity();
        existing.setId(300L);
        existing.setNumber(7);
        existing.setUpdatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        existing.setMerged(true);
        existing.setStatsStatus(StatsStatus.COMPLETE);
        existing.setAdditions(50);
        when(prRepository.findByRepository(repo)).thenReturn(List.of(existing));
        when(prRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        String body = """
                [{"number":7,"title":"Merged done","state":"closed",
                  "user":{"login":"dave"},
                  "created_at":"2024-01-01T00:00:00Z",
                  "updated_at":"2024-01-03T00:00:00Z",
                  "merged_at":"2024-01-02T00:00:00Z","closed_at":"2024-01-02T00:00:00Z",
                  "comments":0,"review_comments":0}]""";
        stubFor(get(urlPathMatching("/repos/owner/test-repo/pulls.*"))
                .willReturn(aResponse().withStatus(200).withBody(body)
                        .withHeader("Content-Type", "application/json")));

        GitHubPullRequestCollector.IngestResult result = service.collectPullRequests(10L, null);

        assertThat(result.savedEntities()).hasSize(1);
        GitHubPullRequestEntity pr = result.savedEntities().get(0);
        assertThat(pr.getStatsStatus()).isEqualTo(StatsStatus.COMPLETE);
        assertThat(pr.getAdditions()).isEqualTo(50);
    }

    // ── non-200 response → GitHubException ─────────────────────────────────

    @Test
    void collectPullRequests_nonOkResponse_throwsGitHubException() {
        GitRepositoryEntity repo = repo();
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("token");
        when(prRepository.findByRepository(repo)).thenReturn(List.of());

        stubFor(get(urlPathMatching("/repos/owner/test-repo/pulls.*"))
                .willReturn(aResponse().withStatus(403).withBody("forbidden")));

        assertThatThrownBy(() -> service.collectPullRequests(10L, null))
                .isInstanceOf(GitHubException.class)
                .hasMessageContaining("403");
    }

    // ── jobState: phaseTotal set from Link header ───────────────────────────

    @Test
    void collectPullRequests_withJobState_setsPhaseTotalFromLinkHeader() {
        GitRepositoryEntity repo = repo();
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("token");
        when(prRepository.findByRepository(repo)).thenReturn(List.of());

        // Generic fallback stub registered first so the specific per_page=1 stub wins.
        stubFor(get(urlPathMatching("/repos/owner/test-repo/pulls.*"))
                .willReturn(aResponse().withStatus(200).withBody("[]")
                        .withHeader("Content-Type", "application/json")));

        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/pulls"))
                .withQueryParam("per_page", equalTo("1"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Link", "<http://x.com?page=17>; rel=\"last\"")
                        .withBody("")));

        SyncJobTracker.JobState jobState = new SyncJobTracker.JobState();
        service.collectPullRequests(10L, jobState);

        assertThat(jobState.phaseTotal).isEqualTo(17);
    }

    // ── multi-page pagination collects all PRs ──────────────────────────────

    @Test
    void collectPullRequests_multiplePages_collectsAllPrs() {
        GitRepositoryEntity repo = repo();
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(any())).thenReturn("token");
        when(prRepository.findByRepository(repo)).thenReturn(List.of());
        when(prRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        String page1 = """
                [{"number":1,"title":"PR1","state":"open","user":{"login":"a"},
                  "created_at":"2024-01-02T00:00:00Z","updated_at":"2024-01-02T00:00:00Z",
                  "merged_at":null,"closed_at":null,"comments":0,"review_comments":0}]""";
        String page2 = """
                [{"number":2,"title":"PR2","state":"open","user":{"login":"b"},
                  "created_at":"2024-01-01T00:00:00Z","updated_at":"2024-01-01T00:00:00Z",
                  "merged_at":null,"closed_at":null,"comments":0,"review_comments":0}]""";

        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/pulls"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(aResponse().withStatus(200).withBody(page1)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Link", "<" + wmBaseUrl
                                + "/repos/owner/test-repo/pulls?state=all&per_page=100&page=2>; rel=\"next\"")));

        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/pulls"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(aResponse().withStatus(200).withBody(page2)
                        .withHeader("Content-Type", "application/json")));

        GitHubPullRequestCollector.IngestResult result = service.collectPullRequests(10L, null);

        assertThat(result.savedEntities()).hasSize(2);
    }

    // ── listPullRequests: delegates to repository ───────────────────────────

    @Test
    void listPullRequests_repoFound_returnsPage() {
        GitRepositoryEntity repo = repo();
        when(repoRepository.findById(10L)).thenReturn(Optional.of(repo));

        GitHubPullRequestEntity pr = new GitHubPullRequestEntity();
        pr.setNumber(1);
        Page<GitHubPullRequestEntity> page =
                new PageImpl<>(List.of(pr), PageRequest.of(0, 20), 1);
        when(prRepository.findByRepositoryOrderByCreatedAtDesc(repo, PageRequest.of(0, 20)))
                .thenReturn(page);

        Page<GitHubPullRequestEntity> result =
                service.listPullRequests(10L, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getNumber()).isEqualTo(1);
    }

    // ── listPullRequests: repo not found ────────────────────────────────────

    @Test
    void listPullRequests_repoNotFound_throwsNoSuchElement() {
        when(repoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listPullRequests(99L, PageRequest.of(0, 20)))
                .isInstanceOf(NoSuchElementException.class);
    }
}
