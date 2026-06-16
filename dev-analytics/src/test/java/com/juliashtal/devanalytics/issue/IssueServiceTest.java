package com.juliashtal.devanalytics.issue;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.issue.model.IssueEntity;
import com.juliashtal.devanalytics.issue.service.IssueService;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueServiceTest {

    @Mock IssueRepository repository;
    @InjectMocks IssueService service;

    @Test
    void getIssue_existingId_returnsEntity() {
        IssueEntity issue = new IssueEntity();
        issue.setId(5L);
        when(repository.findById(5L)).thenReturn(Optional.of(issue));

        assertThat(service.getIssue(5L)).isSameAs(issue);
    }

    @Test
    void getIssue_missingId_throwsNoSuchElement() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getIssue(99L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getByDataSource_delegatesToRepository() {
        DataSourceConfig source = new DataSourceConfig();
        source.setId(1L);
        Page<IssueEntity> page = new PageImpl<>(List.of(new IssueEntity()));
        when(repository.findByDataSource(source, PageRequest.of(0, 10))).thenReturn(page);

        assertThat(service.getByDataSource(source, PageRequest.of(0, 10))).isSameAs(page);
    }

    @Test
    void getByJiraProject_delegatesToRepository() {
        JiraProjectEntity project = new JiraProjectEntity();
        project.setId(2L);
        Page<IssueEntity> page = new PageImpl<>(List.of());
        when(repository.findByJiraProject(project, PageRequest.of(0, 10))).thenReturn(page);

        assertThat(service.getByJiraProject(project, PageRequest.of(0, 10))).isSameAs(page);
    }

    @Test
    void getByRepository_delegatesToRepository() {
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(3L);
        Page<IssueEntity> page = new PageImpl<>(List.of());
        when(repository.findByRepository(repo, PageRequest.of(0, 10))).thenReturn(page);

        assertThat(service.getByRepository(repo, PageRequest.of(0, 10))).isSameAs(page);
    }

    @Test
    void countByRepository_returnsOpenAndClosedCounts() {
        when(repository.countByRepository_IdAndState(3L, "open")).thenReturn(4L);
        when(repository.countByRepository_IdAndState(3L, "closed")).thenReturn(2L);

        assertThat(service.countByRepository(3L)).isEqualTo(Map.of("open", 4L, "closed", 2L));
    }

    @Test
    void countByJiraProject_returnsOpenAndClosedCounts() {
        when(repository.countByJiraProject_Id(2L)).thenReturn(10L);
        when(repository.countByJiraProject_IdAndClosedAtIsNotNull(2L)).thenReturn(6L);

        assertThat(service.countByJiraProject(2L)).isEqualTo(Map.of("open", 4L, "closed", 6L));
    }
}
