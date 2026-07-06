package com.juliashtal.devanalytics.git;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.RepoType;
import com.juliashtal.devanalytics.git.model.UserRepoRegistration;
import com.juliashtal.devanalytics.git.model.dto.RepoDto;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.git.service.RepoService;
import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Covers the mutating and URL/DTO-mapping paths of {@link RepoService} not exercised by
 * {@link RepoServiceAccessibleTest} (subscribe/unsubscribe/setCollectIssues, repo URL building,
 * team filtering).
 */
@ExtendWith(MockitoExtension.class)
class RepoServiceMutationTest {

    @Mock GitRepositoryEntityRepository gitRepoRepository;
    @Mock UserRepoRegistrationRepository userRepoRegRepository;
    @Mock UserRepository userRepository;

    @InjectMocks RepoService repoService;

    private MockedStatic<SecurityUtils> securityUtils;

    private static final Long USER_ID = 1L;
    private static final Long REPO_ID = 5L;

    @BeforeEach
    void setUp() {
        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    private GitRepositoryEntity githubRepo(Long id, String baseUrl, String fullName) {
        DataSourceConfig ds = new DataSourceConfig();
        ds.setId(10L);
        ds.setType(DataSourceType.GITHUB);
        ds.setBaseUrl(baseUrl);

        GitRepositoryEntity r = new GitRepositoryEntity();
        r.setId(id);
        r.setName("repo-" + id);
        r.setRepoType(RepoType.GITHUB);
        r.setRepoFullName(fullName);
        r.setDataSourceConfig(ds);
        return r;
    }

    // ── getById ─────────────────────────────────────────────────────────────────

    @Test
    void getById_notFound_throwsNoSuchElement() {
        when(gitRepoRepository.findById(REPO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> repoService.getById(REPO_ID))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Git repo not found: " + REPO_ID);
    }

    // ── subscribe ─────────────────────────────────────────────────────────────────

    @Test
    void subscribe_notYetSubscribed_savesRegistration() {
        GitRepositoryEntity repo = githubRepo(REPO_ID, "https://api.github.com", "o/r");
        User proxy = new User();
        when(userRepoRegRepository.existsByUserIdAndRepositoryId(USER_ID, REPO_ID)).thenReturn(false);
        when(gitRepoRepository.findById(REPO_ID)).thenReturn(Optional.of(repo));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(proxy);

        repoService.subscribe(REPO_ID);

        var captor = org.mockito.ArgumentCaptor.forClass(UserRepoRegistration.class);
        verify(userRepoRegRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(proxy);
        assertThat(captor.getValue().getRepository()).isSameAs(repo);
    }

    @Test
    void subscribe_alreadySubscribed_isNoOp() {
        when(userRepoRegRepository.existsByUserIdAndRepositoryId(USER_ID, REPO_ID)).thenReturn(true);

        repoService.subscribe(REPO_ID);

        verify(gitRepoRepository, never()).findById(any());
        verify(userRepoRegRepository, never()).save(any());
    }

    @Test
    void subscribe_repoNotFound_throwsNoSuchElement() {
        when(userRepoRegRepository.existsByUserIdAndRepositoryId(USER_ID, REPO_ID)).thenReturn(false);
        when(gitRepoRepository.findById(REPO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> repoService.subscribe(REPO_ID))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Repo not found: " + REPO_ID);
        verify(userRepoRegRepository, never()).save(any());
    }

    // ── unsubscribe ───────────────────────────────────────────────────────────────

    @Test
    void unsubscribe_existingRegistration_deletesIt() {
        UserRepoRegistration reg = new UserRepoRegistration();
        when(userRepoRegRepository.findByUserIdAndRepositoryId(USER_ID, REPO_ID))
                .thenReturn(Optional.of(reg));

        repoService.unsubscribe(REPO_ID);

        verify(userRepoRegRepository).delete(reg);
    }

    @Test
    void unsubscribe_noRegistration_isNoOp() {
        when(userRepoRegRepository.findByUserIdAndRepositoryId(USER_ID, REPO_ID))
                .thenReturn(Optional.empty());

        repoService.unsubscribe(REPO_ID);

        verify(userRepoRegRepository, never()).delete(any());
    }

    // ── setCollectIssues ────────────────────────────────────────────────────────

    @Test
    void setCollectIssues_enabled_savesFlagAndFiresAsyncTrigger() {
        GitRepositoryEntity repo = githubRepo(REPO_ID, "https://api.github.com", "o/r");
        when(gitRepoRepository.findById(REPO_ID)).thenReturn(Optional.of(repo));
        when(userRepoRegRepository.findRepoIdsByUserId(USER_ID)).thenReturn(List.of(REPO_ID));
        AtomicReference<Long> triggered = new AtomicReference<>();

        RepoDto dto = repoService.setCollectIssues(REPO_ID, true, triggered::set);

        assertThat(repo.isCollectIssues()).isTrue();
        assertThat(dto.collectIssues()).isTrue();
        assertThat(dto.subscribed()).isTrue();
        assertThat(triggered.get()).isEqualTo(REPO_ID);
        verify(gitRepoRepository).save(repo);
    }

    @Test
    void setCollectIssues_disabled_savesFlagAndDoesNotFireTrigger() {
        GitRepositoryEntity repo = githubRepo(REPO_ID, "https://api.github.com", "o/r");
        repo.setCollectIssues(true);
        when(gitRepoRepository.findById(REPO_ID)).thenReturn(Optional.of(repo));
        when(userRepoRegRepository.findRepoIdsByUserId(USER_ID)).thenReturn(List.of());
        AtomicReference<Long> triggered = new AtomicReference<>();

        RepoDto dto = repoService.setCollectIssues(REPO_ID, false, triggered::set);

        assertThat(repo.isCollectIssues()).isFalse();
        assertThat(dto.collectIssues()).isFalse();
        assertThat(dto.subscribed()).isFalse();
        assertThat(triggered.get()).isNull();
    }

    @Test
    void setCollectIssues_repoNotFound_throwsNoSuchElement() {
        when(gitRepoRepository.findById(REPO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> repoService.setCollectIssues(REPO_ID, true, id -> {}))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Repo not found: " + REPO_ID);
    }

    // ── repo URL construction via getDtos (listAccessible) ─────────────────────────

    @Test
    void listAccessible_githubRepo_buildsPublicWebUrl() {
        GitRepositoryEntity repo = githubRepo(REPO_ID, "https://api.github.com", "octo/hello");
        when(gitRepoRepository.findAccessibleRepoIds(USER_ID)).thenReturn(List.of(REPO_ID));
        when(userRepoRegRepository.findRepoIdsByUserId(USER_ID)).thenReturn(List.of());
        when(gitRepoRepository.findAllByIdWithDataSourceConfig(List.of(REPO_ID))).thenReturn(List.of(repo));

        RepoDto dto = repoService.listAccessible(null).get(0);

        assertThat(dto.repoUrl()).isEqualTo("https://github.com/octo/hello");
    }

    @Test
    void listAccessible_enterpriseGithubRepo_stripsApiV3Suffix() {
        GitRepositoryEntity repo = githubRepo(REPO_ID, "https://ghe.corp.com/api/v3", "team/svc");
        when(gitRepoRepository.findAccessibleRepoIds(USER_ID)).thenReturn(List.of(REPO_ID));
        when(userRepoRegRepository.findRepoIdsByUserId(USER_ID)).thenReturn(List.of());
        when(gitRepoRepository.findAllByIdWithDataSourceConfig(List.of(REPO_ID))).thenReturn(List.of(repo));

        RepoDto dto = repoService.listAccessible(null).get(0);

        assertThat(dto.repoUrl()).isEqualTo("https://ghe.corp.com/team/svc");
    }

    @Test
    void listAccessible_localRepo_hasNoRepoUrl() {
        DataSourceConfig ds = new DataSourceConfig();
        ds.setId(10L);
        ds.setType(DataSourceType.GIT_LOCAL);
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(REPO_ID);
        repo.setName("local");
        repo.setRepoType(RepoType.LOCAL);
        repo.setLocalPath("/tmp/x");
        repo.setDataSourceConfig(ds);
        when(gitRepoRepository.findAccessibleRepoIds(USER_ID)).thenReturn(List.of(REPO_ID));
        when(userRepoRegRepository.findRepoIdsByUserId(USER_ID)).thenReturn(List.of());
        when(gitRepoRepository.findAllByIdWithDataSourceConfig(List.of(REPO_ID))).thenReturn(List.of(repo));

        RepoDto dto = repoService.listAccessible(null).get(0);

        assertThat(dto.repoUrl()).isNull();
        assertThat(dto.localPath()).isEqualTo("/tmp/x");
    }

    @Test
    void listAccessible_withTeamFilter_keepsOnlyTeamRepos() {
        GitRepositoryEntity teamRepo = githubRepo(REPO_ID, "https://api.github.com", "o/team-repo");
        Team team = new Team();
        team.setId(3L);
        teamRepo.getDataSourceConfig().setTeam(team);

        when(gitRepoRepository.findAccessibleRepoIds(USER_ID)).thenReturn(List.of(REPO_ID, 6L));
        when(gitRepoRepository.findIdsByTeamIds(List.of(3L))).thenReturn(List.of(REPO_ID));
        when(userRepoRegRepository.findRepoIdsByUserId(USER_ID)).thenReturn(List.of());
        when(gitRepoRepository.findAllByIdWithDataSourceConfig(List.of(REPO_ID))).thenReturn(List.of(teamRepo));

        List<RepoDto> result = repoService.listAccessible(null, 3L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(REPO_ID);
        assertThat(result.get(0).teamId()).isEqualTo(3L);
    }

    @Test
    void listAccessible_withTeamFilter_noOverlap_returnsEmpty() {
        when(gitRepoRepository.findAccessibleRepoIds(USER_ID)).thenReturn(List.of(REPO_ID));
        when(gitRepoRepository.findIdsByTeamIds(List.of(3L))).thenReturn(List.of(99L));

        List<RepoDto> result = repoService.listAccessible(null, 3L);

        assertThat(result).isEmpty();
        verify(gitRepoRepository, never()).findAllByIdWithDataSourceConfig(any());
    }
}
