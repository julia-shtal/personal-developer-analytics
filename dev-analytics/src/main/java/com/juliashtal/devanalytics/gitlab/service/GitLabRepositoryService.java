package com.juliashtal.devanalytics.gitlab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.exception.BadRequestException;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.exception.GitLabException;
import com.juliashtal.devanalytics.exception.NotFoundException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.RepoType;
import com.juliashtal.devanalytics.git.model.UserRepoRegistration;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.github.model.dto.DiscoveredRepoDto;
import com.juliashtal.devanalytics.github.model.dto.DiscoveryResult;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitLabRepositoryService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final GitRepositoryEntityRepository repoRepository;
    private final DataSourceConfigRepository dataSourceRepository;
    private final UserRepository userRepository;
    private final UserRepoRegistrationRepository userRepoRegRepository;
    private final GitLabClientFactory gitLabClientFactory;
    private final ObjectMapper objectMapper;

    @Transactional
    public GitRepositoryEntity registerGitLabRepo(Long userId, Long dataSourceId, String fullName) {
        User user = userRepository.getReferenceById(userId);

        Optional<GitRepositoryEntity> existing = repoRepository.findByRepoFullName(fullName);
        if (existing.isPresent()) {
            GitRepositoryEntity repo = existing.get();
            if (userRepoRegRepository.findByUserIdAndRepositoryId(userId, repo.getId()).isEmpty()) {
                UserRepoRegistration reg = new UserRepoRegistration();
                reg.setUser(user);
                reg.setRepository(repo);
                userRepoRegRepository.save(reg);
                log.info("User {} subscribed to existing GitLab repo: {}", userId, fullName);
            }
            return repo;
        }

        if (dataSourceId == null) {
            throw new IllegalArgumentException("dataSourceId is required when registering a new GitLab repo");
        }
        DataSourceConfig cfg = dataSourceRepository.findById(dataSourceId)
                .orElseThrow(() -> new NoSuchElementException("DataSource not found: " + dataSourceId));
        if (!cfg.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("DataSource does not belong to current user");
        }
        if (cfg.getType() != DataSourceType.GITLAB) {
            throw new IllegalArgumentException("DataSource must be of type GITLAB");
        }

        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setDataSourceConfig(cfg);
        repo.setRepoType(RepoType.GITLAB);
        repo.setName(fullName);
        repo.setRepoFullName(fullName);
        repo.setLocalPath(null);
        repo.setLastFetchedCommitHash(null);
        repo.setLastScanAt(null);
        repo = repoRepository.save(repo);

        UserRepoRegistration reg = new UserRepoRegistration();
        reg.setUser(user);
        reg.setRepository(repo);
        userRepoRegRepository.save(reg);
        log.info("Registered new GitLab repo: {} for userId={}", fullName, userId);
        return repo;
    }

    /**
     * Discovers all GitLab projects visible to the datasource's stored token.
     * Results are cached for 60 seconds per datasource.
     */
    @Cacheable(value = "gitlab-discover-repos", key = "#dataSourceId")
    public DiscoveryResult discoverRepos(Long userId, Long dataSourceId) {
        DataSourceConfig cfg = dataSourceRepository.findById(dataSourceId)
                .orElseThrow(() -> new NotFoundException("DataSource not found: " + dataSourceId));

        if (!cfg.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Only the datasource owner can discover repositories");
        }
        if (cfg.getType() != DataSourceType.GITLAB) {
            throw new BadRequestException("Only GITLAB datasources support GitLab repo discovery");
        }

        String apiBase = gitLabClientFactory.resolveApiBase(cfg);
        String token = gitLabClientFactory.getDecryptedToken(cfg);

        Set<String> attached = repoRepository.findAllByDataSourceConfig(cfg).stream()
                .map(GitRepositoryEntity::getRepoFullName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<DiscoveredRepoDto> repos = new ArrayList<>();
        boolean truncated = false;
        int page = 1;

        try {
            while (true) {
                String url = apiBase + "/projects?membership=true&per_page=100&page=" + page;
                HttpResponse<String> response = HTTP_CLIENT.send(
                        gitLabClientFactory.buildRequest(url, token),
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    throw new GitLabException("GitLab token rejected (HTTP " + response.statusCode() + ") for datasource=" + dataSourceId);
                }
                if (response.statusCode() == 429) {
                    log.warn("GitLab rate limit hit during discovery for datasource={}", dataSourceId);
                    truncated = true;
                    break;
                }
                if (response.statusCode() != 200) break;

                JsonNode projects = objectMapper.readTree(response.body());
                if (!projects.isArray() || projects.isEmpty()) break;

                for (JsonNode project : projects) {
                    String fullName = project.path("path_with_namespace").asText(null);
                    if (fullName == null || fullName.isBlank()) continue;
                    repos.add(new DiscoveredRepoDto(
                            fullName,
                            !"public".equals(project.path("visibility").asText("private")),
                            project.path("default_branch").asText("main"),
                            attached.contains(fullName)
                    ));
                }

                String nextPage = response.headers().firstValue("X-Next-Page").orElse("").strip();
                if (nextPage.isBlank()) break;
                page = Integer.parseInt(nextPage);
            }
        } catch (GitLabException e) {
            throw e;
        } catch (Exception e) {
            throw new GitLabException("GitLab repo discovery failed: " + e.getMessage(), e);
        }

        log.info("Discovered {} GitLab repos for datasource={}, truncated={}", repos.size(), dataSourceId, truncated);
        return new DiscoveryResult(repos, truncated);
    }
}
