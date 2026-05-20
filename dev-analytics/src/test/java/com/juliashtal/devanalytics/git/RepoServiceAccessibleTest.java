package com.juliashtal.devanalytics.git;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.RepoType;
import com.juliashtal.devanalytics.git.model.dto.RepoDto;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.git.service.RepoService;
import com.juliashtal.devanalytics.security.SecurityUtils;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepoServiceAccessibleTest {

    @Mock GitRepositoryEntityRepository gitRepoRepository;
    @Mock UserRepoRegistrationRepository userRepoRegRepository;
    @Mock UserRepository userRepository;

    @InjectMocks RepoService repoService;

    private MockedStatic<SecurityUtils> securityUtils;

    private static final Long USER_ID = 1L;
    private static final Long DS_ID   = 10L;

    @BeforeEach
    void setUp() {
        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    // ── listAccessible(null) — no datasource filter ───────────────────────────

    @Test
    void listAccessible_noFilter_delegatesToViewQuery() {
        when(gitRepoRepository.findAccessibleRepoIds(USER_ID)).thenReturn(List.of(1L, 2L));
        when(userRepoRegRepository.findRepoIdsByUserId(USER_ID)).thenReturn(List.of(2L));
        when(gitRepoRepository.findAllByIdWithDataSourceConfig(List.of(1L, 2L)))
            .thenReturn(List.of(repo(1L, DS_ID), repo(2L, DS_ID)));

        List<RepoDto> result = repoService.listAccessible(null);

        assertThat(result).hasSize(2);
        verify(gitRepoRepository).findAccessibleRepoIds(USER_ID);
        verify(gitRepoRepository, never()).findAccessibleRepoIdsByDataSource(any(), any());
    }

    @Test
    void listAccessible_withFilter_delegatesToFilteredViewQuery() {
        when(gitRepoRepository.findAccessibleRepoIdsByDataSource(USER_ID, DS_ID))
            .thenReturn(List.of(1L));
        when(userRepoRegRepository.findRepoIdsByUserId(USER_ID)).thenReturn(List.of());
        when(gitRepoRepository.findAllByIdWithDataSourceConfig(List.of(1L)))
            .thenReturn(List.of(repo(1L, DS_ID)));

        List<RepoDto> result = repoService.listAccessible(DS_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
        verify(gitRepoRepository).findAccessibleRepoIdsByDataSource(USER_ID, DS_ID);
        verify(gitRepoRepository, never()).findAccessibleRepoIds(any());
    }

    @Test
    void listAccessible_emptyViewResult_returnsEmptyList() {
        when(gitRepoRepository.findAccessibleRepoIds(USER_ID)).thenReturn(List.of());

        List<RepoDto> result = repoService.listAccessible(null);

        assertThat(result).isEmpty();
        verify(gitRepoRepository, never()).findAllByIdWithDataSourceConfig(any());
    }

    @Test
    void listAccessible_subscribedFlag_setCorrectly() {
        when(gitRepoRepository.findAccessibleRepoIds(USER_ID)).thenReturn(List.of(1L, 2L));
        // user is subscribed to repo 2 (owned by someone else)
        when(userRepoRegRepository.findRepoIdsByUserId(USER_ID)).thenReturn(List.of(2L));
        when(gitRepoRepository.findAllByIdWithDataSourceConfig(List.of(1L, 2L)))
            .thenReturn(List.of(repo(1L, DS_ID), repo(2L, 99L)));

        List<RepoDto> result = repoService.listAccessible(null);

        RepoDto owned = result.stream().filter(r -> r.id() == 1L).findFirst().orElseThrow();
        RepoDto subscribed = result.stream().filter(r -> r.id() == 2L).findFirst().orElseThrow();
        assertThat(owned.subscribed()).isFalse();
        assertThat(subscribed.subscribed()).isTrue();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private GitRepositoryEntity repo(Long id, Long dsId) {
        DataSourceConfig ds = new DataSourceConfig();
        ds.setId(dsId);
        ds.setType(DataSourceType.GITHUB);

        GitRepositoryEntity r = new GitRepositoryEntity();
        r.setId(id);
        r.setName("repo-" + id);
        r.setRepoType(RepoType.GITHUB);
        r.setRepoFullName("owner/repo-" + id);
        r.setDataSourceConfig(ds);
        return r;
    }
}
