package com.juliashtal.devanalytics.jira;

import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.dto.RepoDto;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.model.JiraProjectRepoMapping;
import com.juliashtal.devanalytics.jira.repository.JiraProjectRepoMappingRepository;
import com.juliashtal.devanalytics.jira.service.JiraProjectMappingService;
import com.juliashtal.devanalytics.jira.service.JiraProjectService;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JiraProjectMappingServiceTest {

    @Mock JiraProjectService jiraProjectService;
    @Mock JiraProjectRepoMappingRepository mappingRepository;
    @Mock UserRepoRegistrationRepository userRepoRegistrationRepository;
    @Mock GitRepositoryEntityRepository gitRepositoryRepository;

    @InjectMocks JiraProjectMappingService service;

    private static final Long USER_ID = 1L;
    private static final Long PROJECT_ID = 10L;
    private static final Long REPO_ID = 20L;

    private JiraProjectEntity project() {
        JiraProjectEntity p = new JiraProjectEntity();
        p.setId(PROJECT_ID);
        p.setProjectKey("PDA");
        return p;
    }

    private GitRepositoryEntity repo() {
        GitRepositoryEntity r = new GitRepositoryEntity();
        r.setId(REPO_ID);
        r.setName("my-repo");
        r.setRepoFullName("owner/my-repo");
        DataSourceConfig ds = new DataSourceConfig();
        ds.setId(5L);
        r.setDataSourceConfig(ds);
        return r;
    }

    @Test
    void link_createsMapping_whenNotAlreadyLinked() {
        when(jiraProjectService.getProjectForUser(PROJECT_ID, USER_ID)).thenReturn(project());
        when(userRepoRegistrationRepository.existsByUserIdAndRepositoryId(USER_ID, REPO_ID)).thenReturn(true);
        when(mappingRepository.existsByJiraProject_IdAndRepository_Id(PROJECT_ID, REPO_ID)).thenReturn(false);
        when(gitRepositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repo()));

        service.link(USER_ID, PROJECT_ID, REPO_ID);

        ArgumentCaptor<JiraProjectRepoMapping> captor = ArgumentCaptor.forClass(JiraProjectRepoMapping.class);
        verify(mappingRepository).save(captor.capture());
        assertThat(captor.getValue().getRepository().getId()).isEqualTo(REPO_ID);
        assertThat(captor.getValue().getJiraProject().getId()).isEqualTo(PROJECT_ID);
    }

    @Test
    void link_isIdempotent_whenAlreadyLinked() {
        when(jiraProjectService.getProjectForUser(PROJECT_ID, USER_ID)).thenReturn(project());
        when(userRepoRegistrationRepository.existsByUserIdAndRepositoryId(USER_ID, REPO_ID)).thenReturn(true);
        when(mappingRepository.existsByJiraProject_IdAndRepository_Id(PROJECT_ID, REPO_ID)).thenReturn(true);

        service.link(USER_ID, PROJECT_ID, REPO_ID);

        verify(mappingRepository, never()).save(any());
    }

    @Test
    void link_throwsForbidden_whenRepoNotSubscribed() {
        when(jiraProjectService.getProjectForUser(PROJECT_ID, USER_ID)).thenReturn(project());
        when(userRepoRegistrationRepository.existsByUserIdAndRepositoryId(USER_ID, REPO_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.link(USER_ID, PROJECT_ID, REPO_ID))
                .isInstanceOf(ForbiddenException.class);
        verify(mappingRepository, never()).save(any());
    }

    @Test
    void unlink_deletesMapping() {
        JiraProjectEntity p = project();
        when(jiraProjectService.getProjectForUser(PROJECT_ID, USER_ID)).thenReturn(p);

        service.unlink(USER_ID, PROJECT_ID, REPO_ID);

        verify(mappingRepository).deleteByJiraProjectAndRepository_Id(p, REPO_ID);
    }

    @Test
    void listMappings_returnsRepoDtos() {
        JiraProjectEntity p = project();
        GitRepositoryEntity r = repo();
        JiraProjectRepoMapping mapping = new JiraProjectRepoMapping();
        mapping.setJiraProject(p);
        mapping.setRepository(r);

        when(jiraProjectService.getProjectForUser(PROJECT_ID, USER_ID)).thenReturn(p);
        when(mappingRepository.findAllByJiraProject(p)).thenReturn(List.of(mapping));
        when(userRepoRegistrationRepository.existsByUserIdAndRepositoryId(USER_ID, REPO_ID)).thenReturn(true);

        List<RepoDto> result = service.listMappings(PROJECT_ID, USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(REPO_ID);
        assertThat(result.get(0).name()).isEqualTo("my-repo");
        assertThat(result.get(0).subscribed()).isTrue();
    }
}
