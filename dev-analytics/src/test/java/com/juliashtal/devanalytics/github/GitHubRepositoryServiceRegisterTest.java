package com.juliashtal.devanalytics.github;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.RepoType;
import com.juliashtal.devanalytics.git.model.UserRepoRegistration;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.github.service.GitHubClientFactory;
import com.juliashtal.devanalytics.github.service.GitHubRepositoryService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubRepositoryServiceRegisterTest {

    @Mock GitRepositoryEntityRepository repoRepository;
    @Mock DataSourceConfigRepository dataSourceRepository;
    @Mock UserRepository userRepository;
    @Mock UserRepoRegistrationRepository userRepoRegRepository;
    @Mock GitHubClientFactory gitHubClientFactory;

    @InjectMocks
    GitHubRepositoryService service;

    private static final Long USER_ID = 1L;
    private static final Long DS_ID = 10L;
    private static final String FULL_NAME = "owner/my-repo";

    private User user;
    private DataSourceConfig githubCfg;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_ID);

        githubCfg = new DataSourceConfig();
        githubCfg.setId(DS_ID);
        githubCfg.setType(DataSourceType.GITHUB);
        githubCfg.setUser(user);

        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
    }

    // ── existing repo → user subscribed (no prior registration) ────────────

    @Test
    void registerGitHubRepo_existingRepo_subscribesAndReturnsEntity() {
        GitRepositoryEntity existing = new GitRepositoryEntity();
        existing.setId(100L);
        existing.setRepoFullName(FULL_NAME);

        when(repoRepository.findByRepoFullName(FULL_NAME)).thenReturn(Optional.of(existing));
        when(userRepoRegRepository.findByUserIdAndRepositoryId(USER_ID, 100L))
                .thenReturn(Optional.empty());

        GitRepositoryEntity result = service.registerGitHubRepo(USER_ID, null, FULL_NAME);

        assertThat(result).isSameAs(existing);
        verify(userRepoRegRepository).save(any(UserRepoRegistration.class));
        verify(repoRepository, never()).save(any());
    }

    // ── existing repo → already registered → idempotent ────────────────────

    @Test
    void registerGitHubRepo_alreadySubscribed_doesNotCreateDuplicateRegistration() {
        GitRepositoryEntity existing = new GitRepositoryEntity();
        existing.setId(100L);
        existing.setRepoFullName(FULL_NAME);

        when(repoRepository.findByRepoFullName(FULL_NAME)).thenReturn(Optional.of(existing));
        when(userRepoRegRepository.findByUserIdAndRepositoryId(USER_ID, 100L))
                .thenReturn(Optional.of(new UserRepoRegistration()));

        GitRepositoryEntity result = service.registerGitHubRepo(USER_ID, null, FULL_NAME);

        assertThat(result).isSameAs(existing);
        verify(userRepoRegRepository, never()).save(any());
    }

    // ── new repo + null dataSourceId → IllegalArgumentException ────────────

    @Test
    void registerGitHubRepo_newRepoWithNullDataSourceId_throwsIllegalArgument() {
        when(repoRepository.findByRepoFullName(FULL_NAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerGitHubRepo(USER_ID, null, FULL_NAME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataSourceId is required");
    }

    // ── data source not found → NoSuchElementException ─────────────────────

    @Test
    void registerGitHubRepo_dataSourceNotFound_throwsNoSuchElement() {
        when(repoRepository.findByRepoFullName(FULL_NAME)).thenReturn(Optional.empty());
        when(dataSourceRepository.findById(DS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerGitHubRepo(USER_ID, DS_ID, FULL_NAME))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("DataSource not found");
    }

    // ── data source belongs to a different user → IllegalArgumentException ──

    @Test
    void registerGitHubRepo_dataSourceOwnerMismatch_throwsIllegalArgument() {
        User otherUser = new User();
        otherUser.setId(99L);
        githubCfg.setUser(otherUser);

        when(repoRepository.findByRepoFullName(FULL_NAME)).thenReturn(Optional.empty());
        when(dataSourceRepository.findById(DS_ID)).thenReturn(Optional.of(githubCfg));

        assertThatThrownBy(() -> service.registerGitHubRepo(USER_ID, DS_ID, FULL_NAME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to current user");
    }

    // ── data source type is not GITHUB → IllegalArgumentException ──────────

    @Test
    void registerGitHubRepo_dataSourceNotGithubType_throwsIllegalArgument() {
        githubCfg.setType(DataSourceType.JIRA);

        when(repoRepository.findByRepoFullName(FULL_NAME)).thenReturn(Optional.empty());
        when(dataSourceRepository.findById(DS_ID)).thenReturn(Optional.of(githubCfg));

        assertThatThrownBy(() -> service.registerGitHubRepo(USER_ID, DS_ID, FULL_NAME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be of type GITHUB");
    }

    // ── happy path: new repo saved with correct fields and registration ──────

    @Test
    void registerGitHubRepo_validNewRepo_savesRepoAndRegistration() {
        when(repoRepository.findByRepoFullName(FULL_NAME)).thenReturn(Optional.empty());
        when(dataSourceRepository.findById(DS_ID)).thenReturn(Optional.of(githubCfg));

        GitRepositoryEntity saved = new GitRepositoryEntity();
        saved.setId(200L);
        saved.setRepoFullName(FULL_NAME);
        when(repoRepository.save(any())).thenReturn(saved);

        GitRepositoryEntity result = service.registerGitHubRepo(USER_ID, DS_ID, FULL_NAME);

        assertThat(result.getId()).isEqualTo(200L);
        verify(repoRepository).save(argThat(r ->
                r.getRepoType() == RepoType.GITHUB
                && FULL_NAME.equals(r.getRepoFullName())
                && FULL_NAME.equals(r.getName())));
        verify(userRepoRegRepository).save(any(UserRepoRegistration.class));
    }
}
