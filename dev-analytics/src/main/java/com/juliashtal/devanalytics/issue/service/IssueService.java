package com.juliashtal.devanalytics.issue.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.issue.model.IssueEntity;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Reads unified issues by id.
 */
@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository repository;

    public IssueEntity getIssue(Long issueId) {
        return repository.findById(issueId)
                .orElseThrow(() -> new NoSuchElementException("Issue not found: " + issueId));
    }

    public Page<IssueEntity> getByDataSource(DataSourceConfig source, Pageable pageable) {
        return repository.findByDataSource(source, pageable);
    }

    public Page<IssueEntity> getByJiraProject(JiraProjectEntity project, Pageable pageable) {
        return repository.findByJiraProject(project, pageable);
    }

    public Page<IssueEntity> getByRepository(GitRepositoryEntity repo, Pageable pageable) {
        return repository.findByRepository(repo, pageable);
    }

    public Map<String, Long> countByRepository(Long repositoryId) {
        long open   = repository.countByRepository_IdAndState(repositoryId, "open");
        long closed = repository.countByRepository_IdAndState(repositoryId, "closed");
        return Map.of("open", open, "closed", closed);
    }

    public Map<String, Long> countByJiraProject(Long projectId) {
        long total  = repository.countByJiraProject_Id(projectId);
        long closed = repository.countByJiraProject_IdAndClosedAtIsNotNull(projectId);
        return Map.of("open", total - closed, "closed", closed);
    }
}
