package com.juliashtal.devanalytics.jira.service;

import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.exception.NotFoundException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.dto.RepoDto;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.model.JiraProjectRepoMapping;
import com.juliashtal.devanalytics.jira.repository.JiraProjectRepoMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JiraProjectMappingService {

    private final JiraProjectService jiraProjectService;
    private final JiraProjectRepoMappingRepository mappingRepository;
    private final UserRepoRegistrationRepository userRepoRegistrationRepository;
    private final GitRepositoryEntityRepository gitRepositoryRepository;

    @Transactional
    public void link(Long userId, Long jiraProjectId, Long repositoryId) {
        JiraProjectEntity project = jiraProjectService.getProjectForUser(jiraProjectId, userId);

        if (!userRepoRegistrationRepository.existsByUserIdAndRepositoryId(userId, repositoryId)) {
            throw new ForbiddenException("Repository not subscribed by user: " + repositoryId);
        }

        if (mappingRepository.existsByJiraProject_IdAndRepository_Id(jiraProjectId, repositoryId)) {
            return; // idempotent
        }

        GitRepositoryEntity repo = gitRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new NotFoundException("Repository not found: " + repositoryId));

        JiraProjectRepoMapping mapping = new JiraProjectRepoMapping();
        mapping.setJiraProject(project);
        mapping.setRepository(repo);
        mappingRepository.save(mapping);
    }

    @Transactional
    public void unlink(Long userId, Long jiraProjectId, Long repositoryId) {
        JiraProjectEntity project = jiraProjectService.getProjectForUser(jiraProjectId, userId);
        mappingRepository.deleteByJiraProjectAndRepository_Id(project, repositoryId);
    }

    @Transactional(readOnly = true)
    public List<RepoDto> listMappings(Long jiraProjectId, Long userId) {
        JiraProjectEntity project = jiraProjectService.getProjectForUser(jiraProjectId, userId);
        return mappingRepository.findAllByJiraProject(project).stream()
                .map(m -> toRepoDto(m.getRepository(), userId))
                .toList();
    }

    private RepoDto toRepoDto(GitRepositoryEntity repo, Long userId) {
        boolean subscribed = userRepoRegistrationRepository.existsByUserIdAndRepositoryId(userId, repo.getId());
        return new RepoDto(
                repo.getId(),
                repo.getName(),
                repo.getRepoFullName(),
                repo.getLocalPath(),
                repo.getDataSourceConfig().getId(),
                subscribed,
                repo.getRepoFullName() != null
                        ? "https://github.com/" + repo.getRepoFullName()
                        : null,
                repo.isCollectIssues(),
                repo.getIssuesLastSyncedAt(),
                null
        );
    }
}
