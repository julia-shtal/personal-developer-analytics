package com.juliashtal.devanalytics.jira.repository;

import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.model.JiraProjectRepoMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JiraProjectRepoMappingRepository
        extends JpaRepository<JiraProjectRepoMapping, JiraProjectRepoMapping.PK> {

    List<JiraProjectRepoMapping> findAllByJiraProject(JiraProjectEntity project);
    boolean existsByJiraProject_IdAndRepository_Id(Long jiraProjectId, Long repositoryId);
    void deleteByJiraProjectAndRepository_Id(JiraProjectEntity project, Long repositoryId);
}
