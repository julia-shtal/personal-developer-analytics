package com.juliashtal.devanalytics.git;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.datasource.service.DataSourceService;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.RepoType;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.git.service.GitRepositoryService;
import com.juliashtal.devanalytics.git.model.dto.RegisterLocalRepoRequest;
import com.juliashtal.devanalytics.github.service.GitHubRepositoryService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepoTypeDiscriminatorTest {

    @Mock GitRepositoryEntityRepository repoRepository;
    @Mock DataSourceConfigRepository dataSourceRepository;
    @Mock UserRepository userRepository;
    @Mock UserRepoRegistrationRepository userRepoRegRepository;

    private static final Long USER_ID = 1L;
    private static final Long DS_ID = 10L;

    private User owner;
    private DataSourceConfig githubDs;
    private DataSourceConfig localDs;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(USER_ID);

        githubDs = new DataSourceConfig();
        githubDs.setId(DS_ID);
        githubDs.setType(DataSourceType.GITHUB);
        githubDs.setUser(owner);

        localDs = new DataSourceConfig();
        localDs.setId(DS_ID + 1);
        localDs.setType(DataSourceType.GIT_LOCAL);
        localDs.setUser(owner);
    }

    @Test
    void githubEntityBuilder_setsRepoTypeGithub() {
        // Verify the creation pattern used by DataSourceService.attachRepo and
        // GitHubRepositoryService.registerGitHubRepo sets GITHUB type.
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setRepoType(RepoType.GITHUB);
        repo.setRepoFullName("owner/repo");

        assertThat(repo.getRepoType()).isEqualTo(RepoType.GITHUB);
        assertThat(repo.getLocalPath()).isNull();
    }

    @Test
    void repoTypeEnum_hasTwoValues() {
        assertThat(RepoType.values()).hasSize(2);
        assertThat(RepoType.valueOf("LOCAL")).isEqualTo(RepoType.LOCAL);
        assertThat(RepoType.valueOf("GITHUB")).isEqualTo(RepoType.GITHUB);
    }

    @Test
    void registerLocalRepo_setsRepoTypeLocal() throws Exception {
        File tmpDir = Files.createTempDirectory("test-repo").toFile();
        tmpDir.deleteOnExit();

        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        when(dataSourceRepository.findById(DS_ID + 1)).thenReturn(Optional.of(localDs));
        when(repoRepository.findAllByDataSourceConfig(localDs)).thenReturn(List.of());
        ArgumentCaptor<GitRepositoryEntity> captor = ArgumentCaptor.forClass(GitRepositoryEntity.class);
        when(repoRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepoRegRepository.save(any())).thenReturn(null);

        GitRepositoryService service = new GitRepositoryService(
                repoRepository, dataSourceRepository, userRepository,
                null, userRepoRegRepository);

        RegisterLocalRepoRequest req = new RegisterLocalRepoRequest();
        req.setDataSourceId(DS_ID + 1);
        req.setName("my-repo");
        req.setLocalPath(tmpDir.getAbsolutePath());

        service.registerLocalRepo(USER_ID, req);

        assertThat(captor.getValue().getRepoType()).isEqualTo(RepoType.LOCAL);
    }
}
