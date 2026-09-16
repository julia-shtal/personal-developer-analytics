package com.juliashtal.devanalytics.github.pullrequest;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
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

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Pins the read side: a known repository returns its stored pull requests newest-first,
 * and an unknown one is rejected rather than returning an empty page.
 */
@ExtendWith(MockitoExtension.class)
class GitHubPullRequestQueryServiceTest {

    @Mock GitRepositoryEntityRepository repoRepository;
    @Mock GitHubPullRequestRepository prRepository;

    GitHubPullRequestQueryService service;

    @BeforeEach
    void setUp() {
        service = new GitHubPullRequestQueryService(repoRepository, prRepository);
    }

    /** Only identity matters here — the query path reads no other field. */
    private GitRepositoryEntity repo() {
        GitRepositoryEntity r = new GitRepositoryEntity();
        r.setId(10L);
        return r;
    }

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

    @Test
    void listPullRequests_repoNotFound_throwsNoSuchElement() {
        when(repoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listPullRequests(99L, PageRequest.of(0, 20)))
                .isInstanceOf(NoSuchElementException.class);
    }
}
