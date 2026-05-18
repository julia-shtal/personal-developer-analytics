package com.juliashtal.devanalytics.jira;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.model.JiraProjectRepoMapping;
import com.juliashtal.devanalytics.jira.model.JiraProjectRepoMappingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JiraProjectRepoMappingRepository
        extends JpaRepository<JiraProjectRepoMapping, JiraProjectRepoMappingId> {

    List<JiraProjectRepoMapping> findAllByJiraProject(JiraProjectEntity jiraProject);
    List<JiraProjectRepoMapping> findAllByRepository(GitRepositoryEntity repository);
    boolean existsByJiraProjectAndRepository(JiraProjectEntity jiraProject, GitRepositoryEntity repository);

    /** Returns repository IDs mapped to any of the supplied Jira project IDs. Used by T4.2 metric queries. */
    @Query("SELECT m.repository.id FROM JiraProjectRepoMapping m WHERE m.jiraProject.id IN :projectIds")
    List<Long> findRepositoryIdsByJiraProjectIds(@Param("projectIds") List<Long> projectIds);
}
