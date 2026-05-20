package com.juliashtal.devanalytics.github;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.exception.BadRequestException;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.exception.GitHubException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.github.model.dto.DiscoveredRepoDto;
import com.juliashtal.devanalytics.github.model.dto.DiscoveryResult;
import com.juliashtal.devanalytics.github.service.GitHubClientFactory;
import com.juliashtal.devanalytics.github.service.GitHubRepositoryService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubRepoDiscoveryTest {

    @Mock DataSourceConfigRepository dataSourceRepository;
    @Mock GitRepositoryEntityRepository repoRepository;
    @Mock UserRepoRegistrationRepository userRepoRegRepository;
    @Mock UserRepository userRepository;
    @Mock GitHubClientFactory gitHubClientFactory;

    @InjectMocks GitHubRepositoryService service;

    private static final Long USER_ID = 1L;
    private static final Long DS_ID = 10L;

    private DataSourceConfig githubDs;

    @BeforeEach
    void setUp() {
        User owner = new User();
        owner.setId(USER_ID);

        githubDs = new DataSourceConfig();
        githubDs.setId(DS_ID);
        githubDs.setType(DataSourceType.GITHUB);
        githubDs.setBaseUrl("https://api.github.com");
        githubDs.setUser(owner);

        when(dataSourceRepository.findById(DS_ID)).thenReturn(Optional.of(githubDs));
    }

    @Test
    void discoverRepos_returnsReposWithAlreadyAttachedFlag() throws Exception {
        // One repo already attached, one discovered for the first time
        GitRepositoryEntity attached = attachedRepo("owner/already");
        when(repoRepository.findAllByDataSourceConfig(githubDs)).thenReturn(List.of(attached));

        // Create GHRepository mocks before any when() chain to avoid Mockito stubbing-context issues.
        GHRepository repo1 = ghRepo("owner/already", false, "main");
        GHRepository repo2 = ghRepo("owner/new-repo", true, "develop");

        PagedIterable<GHRepository> paged = pagedIterable(repo1, repo2);

        GitHub github = mock(GitHub.class);
        GHMyself myself = mock(GHMyself.class);
        when(gitHubClientFactory.createClient(githubDs)).thenReturn(github);
        when(github.getMyself()).thenReturn(myself);
        when(myself.listRepositories(100)).thenReturn(paged);

        DiscoveryResult result = service.discoverRepos(USER_ID, DS_ID);

        assertThat(result.truncated()).isFalse();
        assertThat(result.repos()).hasSize(2);

        DiscoveredRepoDto already = result.repos().stream()
                .filter(r -> r.fullName().equals("owner/already")).findFirst().orElseThrow();
        assertThat(already.alreadyAttached()).isTrue();
        assertThat(already.privateRepo()).isFalse();

        DiscoveredRepoDto newRepo = result.repos().stream()
                .filter(r -> r.fullName().equals("owner/new-repo")).findFirst().orElseThrow();
        assertThat(newRepo.alreadyAttached()).isFalse();
        assertThat(newRepo.privateRepo()).isTrue();
        assertThat(newRepo.defaultBranch()).isEqualTo("develop");
    }

    @Test
    void discoverRepos_rateLimitDuringFetch_returnsTruncated() throws Exception {
        when(repoRepository.findAllByDataSourceConfig(githubDs)).thenReturn(List.of());

        GitHub github = mock(GitHub.class);
        when(gitHubClientFactory.createClient(githubDs)).thenReturn(github);
        // getMyself() declares throws IOException — simulate rate limit hit
        when(github.getMyself()).thenThrow(new IOException("API rate limit exceeded for ..."));

        DiscoveryResult result = service.discoverRepos(USER_ID, DS_ID);

        assertThat(result.truncated()).isTrue();
        assertThat(result.repos()).isEmpty();
    }

    @Test
    void discoverRepos_otherIOException_throwsGitHubException() throws Exception {
        when(repoRepository.findAllByDataSourceConfig(githubDs)).thenReturn(List.of());

        GitHub github = mock(GitHub.class);
        when(gitHubClientFactory.createClient(githubDs)).thenReturn(github);
        when(github.getMyself()).thenThrow(new IOException("connection refused"));

        assertThatThrownBy(() -> service.discoverRepos(USER_ID, DS_ID))
                .isInstanceOf(GitHubException.class)
                .hasMessageContaining("discovery failed");
    }

    @Test
    void discoverRepos_nonOwner_throwsForbidden() {
        User other = new User();
        other.setId(99L);
        githubDs.setUser(other);

        assertThatThrownBy(() -> service.discoverRepos(USER_ID, DS_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void discoverRepos_nonGithubDs_throwsBadRequest() {
        githubDs.setType(DataSourceType.JIRA);

        assertThatThrownBy(() -> service.discoverRepos(USER_ID, DS_ID))
                .isInstanceOf(BadRequestException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static GitRepositoryEntity attachedRepo(String fullName) {
        GitRepositoryEntity r = new GitRepositoryEntity();
        r.setRepoFullName(fullName);
        return r;
    }

    private static GHRepository ghRepo(String fullName, boolean isPrivate, String defaultBranch) {
        GHRepository r = mock(GHRepository.class);
        // doReturn avoids actually calling the methods — needed for GHRepository methods that declare throws IOException.
        doReturn(fullName).when(r).getFullName();
        doReturn(isPrivate).when(r).isPrivate();
        doReturn(defaultBranch).when(r).getDefaultBranch();
        return r;
    }

    @SuppressWarnings("unchecked")
    private static PagedIterable<GHRepository> pagedIterable(GHRepository... repos) {
        PagedIterable<GHRepository> iterable = mock(PagedIterable.class);
        // PagedIterable.iterator() returns PagedIterator, so we wrap a regular iterator in a mock.
        PagedIterator<GHRepository> pagedIter = mock(PagedIterator.class);
        Iterator<GHRepository> delegate = List.of(repos).iterator();
        when(pagedIter.hasNext()).thenAnswer(inv -> delegate.hasNext());
        when(pagedIter.next()).thenAnswer(inv -> delegate.next());
        when(iterable.iterator()).thenReturn(pagedIter);
        return iterable;
    }
}
