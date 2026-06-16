package com.juliashtal.devanalytics.github.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.exception.GitHubException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.issue.model.IssueEntity;
import com.juliashtal.devanalytics.issue.model.IssueSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubIssuesCollectorTest {

    @Mock GitHubClientFactory clientFactory;
    @Mock IssueRepository issueRepository;
    @Mock GitRepositoryEntityRepository gitRepositoryEntityRepository;

    @InjectMocks
    GitHubIssuesCollector collector;

    private static final String FULL_NAME = "owner/test-repo";

    private DataSourceConfig cfg;
    private GitRepositoryEntity repoEntity;

    @BeforeEach
    void setUp() {
        cfg = new DataSourceConfig();
        cfg.setId(1L);

        repoEntity = new GitRepositoryEntity();
        repoEntity.setId(10L);
        repoEntity.setRepoFullName(FULL_NAME);
        repoEntity.setName(FULL_NAME);
        repoEntity.setDataSourceConfig(cfg);
    }

    // ── blank fullName → IllegalArgumentException ───────────────────────────

    @Test
    void collectIssuesForRepo_blankFullName_throwsIllegalArgument() {
        assertThatThrownBy(() -> collector.collectIssuesForRepo(cfg, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repoFullName is required");
    }

    // ── GitHub client throws IOException → GitHubException ─────────────────

    @Test
    void collectIssuesForRepo_clientThrowsIO_throwsGitHubException() throws Exception {
        GitHub github = mock(GitHub.class);
        when(clientFactory.createClient(cfg)).thenReturn(github);
        doThrow(new IOException("connection refused")).when(github).getRepository(FULL_NAME);

        assertThatThrownBy(() -> collector.collectIssuesForRepo(cfg, FULL_NAME))
                .isInstanceOf(GitHubException.class)
                .hasMessageContaining("Failed to collect GitHub issues");
    }

    // ── repo entity not found → IllegalStateException ──────────────────────

    @Test
    void collectIssuesForRepo_repoEntityNotFound_throwsIllegalState() throws Exception {
        GitHub github = mock(GitHub.class);
        GHRepository ghRepo = mock(GHRepository.class);

        when(clientFactory.createClient(cfg)).thenReturn(github);
        doReturn(ghRepo).when(github).getRepository(FULL_NAME);
        when(gitRepositoryEntityRepository.findByDataSourceConfigAndName(cfg, FULL_NAME))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> collector.collectIssuesForRepo(cfg, FULL_NAME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GitRepositoryEntity not found");
    }

    // ── empty issue list → saves nothing, returns 0 ─────────────────────────

    @Test
    void collectIssuesForRepo_noIssues_savesNothingReturnsZero() throws Exception {
        GitHub github = mock(GitHub.class);
        GHRepository ghRepo = mock(GHRepository.class);

        when(clientFactory.createClient(cfg)).thenReturn(github);
        doReturn(ghRepo).when(github).getRepository(FULL_NAME);
        when(gitRepositoryEntityRepository.findByDataSourceConfigAndName(cfg, FULL_NAME))
                .thenReturn(Optional.of(repoEntity));

        mockIssueQueryChain(ghRepo, emptyPagedIterable());

        int result = collector.collectIssuesForRepo(cfg, FULL_NAME);

        assertThat(result).isEqualTo(0);
        verify(issueRepository).saveAll(argThat(list -> ((List<?>) list).isEmpty()));
    }

    // ── issue that is actually a PR → skipped ───────────────────────────────

    @Test
    void collectIssuesForRepo_prInList_isSkipped() throws Exception {
        GitHub github = mock(GitHub.class);
        GHRepository ghRepo = mock(GHRepository.class);

        when(clientFactory.createClient(cfg)).thenReturn(github);
        doReturn(ghRepo).when(github).getRepository(FULL_NAME);
        when(gitRepositoryEntityRepository.findByDataSourceConfigAndName(cfg, FULL_NAME))
                .thenReturn(Optional.of(repoEntity));

        GHIssue prIssue = mock(GHIssue.class);
        doReturn(mock(GHIssue.PullRequest.class)).when(prIssue).getPullRequest();

        mockIssueQueryChain(ghRepo, pagedIterable(prIssue));

        int result = collector.collectIssuesForRepo(cfg, FULL_NAME);

        assertThat(result).isEqualTo(0);
    }

    // ── real issue → mapped and saved ───────────────────────────────────────

    @Test
    void collectIssuesForRepo_realIssue_mappedAndSaved() throws Exception {
        GitHub github = mock(GitHub.class);
        GHRepository ghRepo = mock(GHRepository.class);
        GHUser creator = mock(GHUser.class);

        when(clientFactory.createClient(cfg)).thenReturn(github);
        doReturn(ghRepo).when(github).getRepository(FULL_NAME);
        when(gitRepositoryEntityRepository.findByDataSourceConfigAndName(cfg, FULL_NAME))
                .thenReturn(Optional.of(repoEntity));

        GHIssue issue = mock(GHIssue.class);
        doReturn(null).when(issue).getPullRequest();
        doReturn(1).when(issue).getNumber();
        doReturn("Bug report").when(issue).getTitle();
        doReturn("Something is broken").when(issue).getBody();
        doReturn(GHIssueState.OPEN).when(issue).getState();
        doReturn(null).when(issue).getAssignee();
        doReturn(creator).when(issue).getUser();
        doReturn("alice").when(creator).getLogin();
        doReturn(Instant.parse("2024-01-01T00:00:00Z")).when(issue).getCreatedAt();
        doReturn(Instant.parse("2024-01-02T00:00:00Z")).when(issue).getUpdatedAt();
        doReturn(null).when(issue).getClosedAt();
        doReturn(List.of()).when(issue).getLabels();

        when(issueRepository.findByDataSourceAndSourceIssueKey(cfg, FULL_NAME + "#1"))
                .thenReturn(Optional.empty());
        when(issueRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        mockIssueQueryChain(ghRepo, pagedIterable(issue));

        int result = collector.collectIssuesForRepo(cfg, FULL_NAME);

        assertThat(result).isEqualTo(1);
        verify(issueRepository).saveAll(argThat(saved -> {
            @SuppressWarnings("unchecked")
            List<IssueEntity> list = (List<IssueEntity>) saved;
            if (list.size() != 1) return false;
            IssueEntity e = list.get(0);
            return "Bug report".equals(e.getTitle())
                    && IssueSource.GITHUB.equals(e.getSource())
                    && "open".equals(e.getState())
                    && "alice".equals(e.getCreator())
                    && (FULL_NAME + "#1").equals(e.getSourceIssueKey());
        }));
    }

    // ── issue with labels → comma-joined ────────────────────────────────────

    @Test
    void collectIssuesForRepo_issueWithLabels_joinsLabelNames() throws Exception {
        GitHub github = mock(GitHub.class);
        GHRepository ghRepo = mock(GHRepository.class);

        when(clientFactory.createClient(cfg)).thenReturn(github);
        doReturn(ghRepo).when(github).getRepository(FULL_NAME);
        when(gitRepositoryEntityRepository.findByDataSourceConfigAndName(cfg, FULL_NAME))
                .thenReturn(Optional.of(repoEntity));

        GHLabel label1 = mock(GHLabel.class);
        GHLabel label2 = mock(GHLabel.class);
        doReturn("bug").when(label1).getName();
        doReturn("enhancement").when(label2).getName();

        GHIssue issue = mock(GHIssue.class);
        doReturn(null).when(issue).getPullRequest();
        doReturn(2).when(issue).getNumber();
        doReturn("Feature").when(issue).getTitle();
        doReturn("").when(issue).getBody();
        doReturn(GHIssueState.OPEN).when(issue).getState();
        doReturn(null).when(issue).getAssignee();
        doReturn(null).when(issue).getUser();
        doReturn(Instant.parse("2024-01-01T00:00:00Z")).when(issue).getCreatedAt();
        doReturn(Instant.parse("2024-01-01T00:00:00Z")).when(issue).getUpdatedAt();
        doReturn(null).when(issue).getClosedAt();
        doReturn(List.of(label1, label2)).when(issue).getLabels();

        when(issueRepository.findByDataSourceAndSourceIssueKey(cfg, FULL_NAME + "#2"))
                .thenReturn(Optional.empty());
        when(issueRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        mockIssueQueryChain(ghRepo, pagedIterable(issue));

        collector.collectIssuesForRepo(cfg, FULL_NAME);

        verify(issueRepository).saveAll(argThat(saved -> {
            @SuppressWarnings("unchecked")
            List<IssueEntity> list = (List<IssueEntity>) saved;
            return list.size() == 1 && "bug,enhancement".equals(list.get(0).getLabels());
        }));
    }

    // ── entity overload → updates issuesLastSyncedAt ────────────────────────

    @Test
    void collectIssuesForRepo_entityOverload_updatesIssuesLastSyncedAt() throws Exception {
        GitHub github = mock(GitHub.class);
        GHRepository ghRepo = mock(GHRepository.class);

        when(clientFactory.createClient(cfg)).thenReturn(github);
        doReturn(ghRepo).when(github).getRepository(FULL_NAME);
        when(gitRepositoryEntityRepository.findByDataSourceConfigAndName(cfg, FULL_NAME))
                .thenReturn(Optional.of(repoEntity));

        mockIssueQueryChain(ghRepo, emptyPagedIterable());
        when(gitRepositoryEntityRepository.save(repoEntity)).thenReturn(repoEntity);

        collector.collectIssuesForRepo(cfg, repoEntity);

        assertThat(repoEntity.getIssuesLastSyncedAt()).isNotNull();
        verify(gitRepositoryEntityRepository).save(repoEntity);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static PagedIterable<GHIssue> pagedIterable(GHIssue... issues) {
        PagedIterable<GHIssue> iterable = mock(PagedIterable.class);
        PagedIterator<GHIssue> pagedIter = mock(PagedIterator.class);
        Iterator<GHIssue> delegate = List.of(issues).iterator();
        when(pagedIter.hasNext()).thenAnswer(inv -> delegate.hasNext());
        when(pagedIter.next()).thenAnswer(inv -> delegate.next());
        when(iterable.iterator()).thenReturn(pagedIter);
        return iterable;
    }

    @SuppressWarnings("unchecked")
    private static PagedIterable<GHIssue> emptyPagedIterable() {
        PagedIterable<GHIssue> iterable = mock(PagedIterable.class);
        PagedIterator<GHIssue> pagedIter = mock(PagedIterator.class);
        when(pagedIter.hasNext()).thenReturn(false);
        when(iterable.iterator()).thenReturn(pagedIter);
        return iterable;
    }

    @SuppressWarnings("unchecked")
    private static void mockIssueQueryChain(GHRepository ghRepo,
                                            PagedIterable<GHIssue> issueIterable)
            throws IOException {
        // GHRepository.queryIssues() returns the concrete ForRepository subtype, not the abstract base.
        GHIssueQueryBuilder.ForRepository builder = mock(GHIssueQueryBuilder.ForRepository.class);
        doReturn(builder).when(ghRepo).queryIssues();
        doReturn(builder).when(builder).state(GHIssueState.ALL);
        doReturn(issueIterable).when(builder).list();
        doReturn(issueIterable).when(issueIterable).withPageSize(100);
    }
}
