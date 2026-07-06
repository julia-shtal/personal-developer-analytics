package com.juliashtal.devanalytics.git;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.RepoType;
import com.juliashtal.devanalytics.git.model.UserRepoRegistration;
import com.juliashtal.devanalytics.git.model.dto.RegisterLocalRepoRequest;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.git.service.GitRepositoryService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitRepositoryServiceTest {

    @Mock GitRepositoryEntityRepository repoRepository;
    @Mock DataSourceConfigRepository dataSourceRepository;
    @Mock UserRepository userRepository;
    @Mock GitCommitEntityRepository commitRepository;
    @Mock UserRepoRegistrationRepository userRepoRegRepository;

    @InjectMocks GitRepositoryService service;

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long DS_ID = 10L;
    private static final Long REPO_ID = 100L;

    private User owner;
    private DataSourceConfig localDs;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(USER_ID);

        localDs = new DataSourceConfig();
        localDs.setId(DS_ID);
        localDs.setType(DataSourceType.GIT_LOCAL);
        localDs.setUser(owner);
    }

    private DataSourceConfig dsOwnedBy(Long ownerId, DataSourceType type) {
        User u = new User();
        u.setId(ownerId);
        DataSourceConfig ds = new DataSourceConfig();
        ds.setId(DS_ID);
        ds.setType(type);
        ds.setUser(u);
        return ds;
    }

    private GitRepositoryEntity repoUnderDs(DataSourceConfig ds) {
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(REPO_ID);
        repo.setName("repo");
        repo.setRepoType(RepoType.LOCAL);
        repo.setDataSourceConfig(ds);
        return repo;
    }

    // ── registerLocalRepo — happy path ──────────────────────────────────────────

    @Test
    void registerLocalRepo_validRequest_savesRepoAndRegistration(@TempDir Path dir) {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        when(dataSourceRepository.findById(DS_ID)).thenReturn(Optional.of(localDs));
        when(repoRepository.findAllByDataSourceConfig(localDs)).thenReturn(List.of());
        when(repoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterLocalRepoRequest req = new RegisterLocalRepoRequest();
        req.setDataSourceId(DS_ID);
        req.setName("my-repo");
        req.setLocalPath(dir.toString());

        GitRepositoryEntity saved = service.registerLocalRepo(USER_ID, req);

        assertThat(saved.getRepoType()).isEqualTo(RepoType.LOCAL);
        assertThat(saved.getName()).isEqualTo("my-repo");
        assertThat(saved.getLocalPath()).isEqualTo(new File(dir.toString()).getAbsolutePath());
        assertThat(saved.getDataSourceConfig()).isSameAs(localDs);
        assertThat(saved.getLastFetchedCommitHash()).isNull();
        assertThat(saved.getLastScanAt()).isNotNull();

        verify(repoRepository).save(saved);

        ArgumentCaptor<UserRepoRegistration> regCaptor = ArgumentCaptor.forClass(UserRepoRegistration.class);
        verify(userRepoRegRepository).save(regCaptor.capture());
        assertThat(regCaptor.getValue().getUser()).isSameAs(owner);
        assertThat(regCaptor.getValue().getRepository()).isSameAs(saved);
    }

    // ── registerLocalRepo — validation / error paths ────────────────────────────

    @Test
    void registerLocalRepo_pathAlreadyRegistered_throwsIllegalArgument(@TempDir Path dir) {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        when(dataSourceRepository.findById(DS_ID)).thenReturn(Optional.of(localDs));

        GitRepositoryEntity existing = repoUnderDs(localDs);
        existing.setLocalPath(new File(dir.toString()).getAbsolutePath());
        when(repoRepository.findAllByDataSourceConfig(localDs)).thenReturn(List.of(existing));

        RegisterLocalRepoRequest req = new RegisterLocalRepoRequest();
        req.setDataSourceId(DS_ID);
        req.setName("dup");
        req.setLocalPath(dir.toString());

        assertThatThrownBy(() -> service.registerLocalRepo(USER_ID, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
        verify(repoRepository, never()).save(any());
        verify(userRepoRegRepository, never()).save(any());
    }

    @Test
    void registerLocalRepo_dataSourceNotFound_throwsNoSuchElement() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        when(dataSourceRepository.findById(DS_ID)).thenReturn(Optional.empty());

        RegisterLocalRepoRequest req = new RegisterLocalRepoRequest();
        req.setDataSourceId(DS_ID);
        req.setName("x");
        req.setLocalPath("/whatever");

        assertThatThrownBy(() -> service.registerLocalRepo(USER_ID, req))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("DataSource not found: " + DS_ID);
    }

    @Test
    void registerLocalRepo_dataSourceOwnedByAnotherUser_throwsIllegalArgument(@TempDir Path dir) {
        DataSourceConfig foreignDs = dsOwnedBy(OTHER_USER_ID, DataSourceType.GIT_LOCAL);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        when(dataSourceRepository.findById(DS_ID)).thenReturn(Optional.of(foreignDs));

        RegisterLocalRepoRequest req = new RegisterLocalRepoRequest();
        req.setDataSourceId(DS_ID);
        req.setName("x");
        req.setLocalPath(dir.toString());

        assertThatThrownBy(() -> service.registerLocalRepo(USER_ID, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void registerLocalRepo_dataSourceNotGitLocalType_throwsIllegalArgument(@TempDir Path dir) {
        DataSourceConfig githubDs = dsOwnedBy(USER_ID, DataSourceType.GITHUB);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        when(dataSourceRepository.findById(DS_ID)).thenReturn(Optional.of(githubDs));

        RegisterLocalRepoRequest req = new RegisterLocalRepoRequest();
        req.setDataSourceId(DS_ID);
        req.setName("x");
        req.setLocalPath(dir.toString());

        assertThatThrownBy(() -> service.registerLocalRepo(USER_ID, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be of type GIT_LOCAL");
    }

    @Test
    void registerLocalRepo_pathIsNotADirectory_throwsIllegalArgument() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        when(dataSourceRepository.findById(DS_ID)).thenReturn(Optional.of(localDs));

        RegisterLocalRepoRequest req = new RegisterLocalRepoRequest();
        req.setDataSourceId(DS_ID);
        req.setName("x");
        req.setLocalPath("/path/that/does/not/exist/xyzzy");

        assertThatThrownBy(() -> service.registerLocalRepo(USER_ID, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a directory");
    }

    // ── listReposForUser ────────────────────────────────────────────────────────

    @Test
    void listReposForUser_returnsOnlyReposOwnedByUser() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);

        GitRepositoryEntity mine = repoUnderDs(localDs);
        GitRepositoryEntity foreign = repoUnderDs(dsOwnedBy(OTHER_USER_ID, DataSourceType.GIT_LOCAL));
        GitRepositoryEntity orphan = repoUnderDs(null);   // null data source → filtered out
        when(repoRepository.findAll()).thenReturn(List.of(mine, foreign, orphan));

        List<GitRepositoryEntity> result = service.listReposForUser(USER_ID);

        assertThat(result).containsExactly(mine);
    }

    // ── getRepoForUser ──────────────────────────────────────────────────────────

    @Test
    void getRepoForUser_ownedRepo_returnsRepo() {
        GitRepositoryEntity repo = repoUnderDs(localDs);
        when(repoRepository.findById(REPO_ID)).thenReturn(Optional.of(repo));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);

        assertThat(service.getRepoForUser(USER_ID, REPO_ID)).isSameAs(repo);
    }

    @Test
    void getRepoForUser_repoNotFound_throwsNoSuchElement() {
        when(repoRepository.findById(REPO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRepoForUser(USER_ID, REPO_ID))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Git repo not found: " + REPO_ID);
    }

    @Test
    void getRepoForUser_repoOwnedByAnotherUser_throwsIllegalArgument() {
        GitRepositoryEntity repo = repoUnderDs(dsOwnedBy(OTHER_USER_ID, DataSourceType.GIT_LOCAL));
        when(repoRepository.findById(REPO_ID)).thenReturn(Optional.of(repo));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);

        assertThatThrownBy(() -> service.getRepoForUser(USER_ID, REPO_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    // ── listCommitsForRepo ──────────────────────────────────────────────────────

    @Test
    void listCommitsForRepo_ownedRepo_delegatesToCommitRepository() {
        GitRepositoryEntity repo = repoUnderDs(localDs);
        Pageable pageable = PageRequest.of(0, 20);
        Page<GitCommitEntity> page = new PageImpl<>(List.of(new GitCommitEntity()));
        when(repoRepository.findById(REPO_ID)).thenReturn(Optional.of(repo));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        when(commitRepository.findByRepositoryIdOrderByAuthorDateDesc(REPO_ID, pageable)).thenReturn(page);

        Page<GitCommitEntity> result = service.listCommitsForRepo(USER_ID, REPO_ID, pageable);

        assertThat(result).isSameAs(page);
        verify(commitRepository).findByRepositoryIdOrderByAuthorDateDesc(REPO_ID, pageable);
    }

    @Test
    void listCommitsForRepo_repoNotFound_throwsNoSuchElement() {
        Pageable pageable = PageRequest.of(0, 20);
        when(repoRepository.findById(REPO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listCommitsForRepo(USER_ID, REPO_ID, pageable))
                .isInstanceOf(NoSuchElementException.class);
        verify(commitRepository, never()).findByRepositoryIdOrderByAuthorDateDesc(any(), any());
    }

    @Test
    void listCommitsForRepo_repoOwnedByAnotherUser_throwsIllegalArgument() {
        GitRepositoryEntity repo = repoUnderDs(dsOwnedBy(OTHER_USER_ID, DataSourceType.GIT_LOCAL));
        Pageable pageable = PageRequest.of(0, 20);
        when(repoRepository.findById(REPO_ID)).thenReturn(Optional.of(repo));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);

        assertThatThrownBy(() -> service.listCommitsForRepo(USER_ID, REPO_ID, pageable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
        verify(commitRepository, never()).findByRepositoryIdOrderByAuthorDateDesc(any(), any());
    }
}
