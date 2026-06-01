package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.datasource.service.DataSourceService;
import com.juliashtal.devanalytics.datasource.service.DataSourceValidator;
import com.juliashtal.devanalytics.exception.BadRequestException;
import com.juliashtal.devanalytics.exception.ConflictException;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.exception.NotFoundException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.UserRepoRegistration;
import com.juliashtal.devanalytics.git.model.dto.RepoDto;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.git.service.GitRepositoryService;
import com.juliashtal.devanalytics.github.service.GitHubRepositoryService;
import com.juliashtal.devanalytics.jira.service.JiraProjectService;
import com.juliashtal.devanalytics.security.TokenEncryptor;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSourceServiceAttachDetachTest {

    @Mock DataSourceConfigRepository repository;
    @Mock UserRepository userRepository;
    @Mock TeamRepository teamRepository;
    @Mock TokenEncryptor tokenEncryptor;
    @Mock DataSourceValidator validator;
    @Mock GitRepositoryService gitRepositoryService;
    @Mock GitHubRepositoryService gitHubRepositoryService;
    @Mock GitRepositoryEntityRepository gitRepoRepository;
    @Mock UserRepoRegistrationRepository userRepoRegRepository;
    @Mock JiraProjectService jiraProjectService;

    @InjectMocks DataSourceService service;

    private static final Long USER_ID = 1L;
    private static final Long DS_ID = 10L;

    private DataSourceConfig githubDs;
    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(USER_ID);

        githubDs = new DataSourceConfig();
        githubDs.setId(DS_ID);
        githubDs.setType(DataSourceType.GITHUB);
        githubDs.setBaseUrl("https://api.github.com");
        githubDs.setUser(owner);

        when(repository.findById(DS_ID)).thenReturn(Optional.of(githubDs));
    }

    // ── attachRepo ────────────────────────────────────────────────────────────

    @Test
    void attachRepo_newRepo_createsAndReturns() {
        when(gitRepoRepository.findByRepoFullName("owner/repo")).thenReturn(Optional.empty());
        GitRepositoryEntity saved = repo(5L, "owner/repo", DS_ID);
        when(gitRepoRepository.save(any())).thenReturn(saved);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        when(userRepoRegRepository.findRepoIdsByUserId(USER_ID)).thenReturn(java.util.List.of(5L));

        RepoDto result = service.attachRepo(USER_ID, DS_ID, "owner/repo", false);

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.repoFullName()).isEqualTo("owner/repo");
        verify(gitRepoRepository).save(any());
        verify(userRepoRegRepository).save(any());
    }

    @Test
    void attachRepo_alreadyUnderThisDs_idempotentReturn() {
        GitRepositoryEntity existing = repo(5L, "owner/repo", DS_ID);
        when(gitRepoRepository.findByRepoFullName("owner/repo")).thenReturn(Optional.of(existing));
        when(userRepoRegRepository.findRepoIdsByUserId(USER_ID)).thenReturn(java.util.List.of(5L));

        RepoDto result = service.attachRepo(USER_ID, DS_ID, "owner/repo", false);

        assertThat(result.id()).isEqualTo(5L);
        verify(gitRepoRepository, never()).save(any());
    }

    @Test
    void attachRepo_crossDs_emptyCallingDs_subscribesAndDeletesOrphan() {
        DataSourceConfig canonicalDs = new DataSourceConfig();
        canonicalDs.setId(99L);
        canonicalDs.setType(DataSourceType.GITHUB);
        canonicalDs.setBaseUrl("https://api.github.com");

        GitRepositoryEntity canonical = repo(5L, "owner/repo", 99L);
        canonical.setDataSourceConfig(canonicalDs);

        when(gitRepoRepository.findByRepoFullName("owner/repo")).thenReturn(Optional.of(canonical));
        when(userRepoRegRepository.findByUserIdAndRepositoryId(USER_ID, 5L))
                .thenReturn(java.util.Optional.empty());
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        // Calling DS has no canonical repos of its own → should be deleted.
        when(gitRepoRepository.countByDataSourceConfig(githubDs)).thenReturn(0L);
        when(userRepoRegRepository.findRepoIdsByUserId(USER_ID)).thenReturn(java.util.List.of(5L));

        RepoDto result = service.attachRepo(USER_ID, DS_ID, "owner/repo", false);

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.dataSourceId()).isEqualTo(99L);
        verify(userRepoRegRepository).save(any(UserRepoRegistration.class));
        verify(repository).delete(githubDs);
    }

    @Test
    void attachRepo_crossDs_nonEmptyCallingDs_subscribesWithoutDeletingDs() {
        DataSourceConfig canonicalDs = new DataSourceConfig();
        canonicalDs.setId(99L);
        canonicalDs.setType(DataSourceType.GITHUB);
        canonicalDs.setBaseUrl("https://api.github.com");

        GitRepositoryEntity canonical = repo(5L, "owner/repo", 99L);
        canonical.setDataSourceConfig(canonicalDs);

        when(gitRepoRepository.findByRepoFullName("owner/repo")).thenReturn(Optional.of(canonical));
        when(userRepoRegRepository.findByUserIdAndRepositoryId(USER_ID, 5L))
                .thenReturn(java.util.Optional.empty());
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        // Calling DS has other repos → must NOT be deleted.
        when(gitRepoRepository.countByDataSourceConfig(githubDs)).thenReturn(2L);
        when(userRepoRegRepository.findRepoIdsByUserId(USER_ID)).thenReturn(java.util.List.of(5L));

        service.attachRepo(USER_ID, DS_ID, "owner/repo", false);

        verify(userRepoRegRepository).save(any(UserRepoRegistration.class));
        verify(repository, never()).delete(any(DataSourceConfig.class));
    }

    @Test
    void attachRepo_crossDs_alreadySubscribed_idempotent() {
        DataSourceConfig canonicalDs = new DataSourceConfig();
        canonicalDs.setId(99L);
        canonicalDs.setType(DataSourceType.GITHUB);
        canonicalDs.setBaseUrl("https://api.github.com");

        GitRepositoryEntity canonical = repo(5L, "owner/repo", 99L);
        canonical.setDataSourceConfig(canonicalDs);

        when(gitRepoRepository.findByRepoFullName("owner/repo")).thenReturn(Optional.of(canonical));
        // Already subscribed — no new registration.
        when(userRepoRegRepository.findByUserIdAndRepositoryId(USER_ID, 5L))
                .thenReturn(java.util.Optional.of(new UserRepoRegistration()));
        when(gitRepoRepository.countByDataSourceConfig(githubDs)).thenReturn(0L);
        when(userRepoRegRepository.findRepoIdsByUserId(USER_ID)).thenReturn(java.util.List.of(5L));

        service.attachRepo(USER_ID, DS_ID, "owner/repo", false);

        verify(userRepoRegRepository, never()).save(any(UserRepoRegistration.class));
    }

    @Test
    void attachRepo_nonOwner_throwsForbidden() {
        User other = new User();
        other.setId(99L);
        githubDs.setUser(other);

        assertThatThrownBy(() -> service.attachRepo(USER_ID, DS_ID, "owner/repo", false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void attachRepo_nonGithubDs_throwsBadRequest() {
        githubDs.setType(DataSourceType.JIRA);

        assertThatThrownBy(() -> service.attachRepo(USER_ID, DS_ID, "owner/repo", false))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("GITHUB");
    }

    // ── detachRepo ────────────────────────────────────────────────────────────

    @Test
    void detachRepo_noSubscribers_deletes() {
        GitRepositoryEntity repoEntity = repo(5L, "owner/repo", DS_ID);
        when(gitRepoRepository.findById(5L)).thenReturn(Optional.of(repoEntity));
        when(userRepoRegRepository.countSubscribersExcludingUser(5L, USER_ID)).thenReturn(0L);

        service.detachRepo(USER_ID, DS_ID, 5L);

        verify(gitRepoRepository).delete(repoEntity);
    }

    @Test
    void detachRepo_hasSubscribers_throws409() {
        GitRepositoryEntity repoEntity = repo(5L, "owner/repo", DS_ID);
        when(gitRepoRepository.findById(5L)).thenReturn(Optional.of(repoEntity));
        when(userRepoRegRepository.countSubscribersExcludingUser(5L, USER_ID)).thenReturn(2L);

        assertThatThrownBy(() -> service.detachRepo(USER_ID, DS_ID, 5L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("2 active subscription");
        verify(gitRepoRepository, never()).delete(any());
    }

    @Test
    void detachRepo_repoUnderDifferentDs_throwsForbidden() {
        DataSourceConfig otherDs = new DataSourceConfig();
        otherDs.setId(99L);
        GitRepositoryEntity repoEntity = repo(5L, "owner/repo", 99L);
        repoEntity.setDataSourceConfig(otherDs);
        when(gitRepoRepository.findById(5L)).thenReturn(Optional.of(repoEntity));

        assertThatThrownBy(() -> service.detachRepo(USER_ID, DS_ID, 5L))
                .isInstanceOf(ForbiddenException.class);
        verify(gitRepoRepository, never()).delete(any());
    }

    @Test
    void detachRepo_repoNotFound_throwsNotFound() {
        when(gitRepoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detachRepo(USER_ID, DS_ID, 99L))
                .isInstanceOf(NotFoundException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static GitRepositoryEntity repo(Long id, String fullName, Long dsId) {
        DataSourceConfig ds = new DataSourceConfig();
        ds.setId(dsId);
        ds.setType(DataSourceType.GITHUB);
        ds.setBaseUrl("https://api.github.com");

        GitRepositoryEntity r = new GitRepositoryEntity();
        r.setId(id);
        r.setName(fullName);
        r.setRepoFullName(fullName);
        r.setDataSourceConfig(ds);
        return r;
    }
}
