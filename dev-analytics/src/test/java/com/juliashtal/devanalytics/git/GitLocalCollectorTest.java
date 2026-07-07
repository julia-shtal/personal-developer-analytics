package com.juliashtal.devanalytics.git;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.exception.GitException;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.service.GitLocalCollector;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Exercises {@link GitLocalCollector} against a real on-disk Git repository built with JGit
 * inside a {@link TempDir}. This is the only way to cover the JGit walk + diff computation
 * with meaningful assertions on the extracted commit metadata and stats.
 *
 * <p>Covers both the root-commit path (diff against the empty tree) and child-commit paths
 * (diff against the parent tree).
 */
@ExtendWith(MockitoExtension.class)
class GitLocalCollectorTest {

    @Mock GitCommitEntityRepository commitRepository;
    @Mock GitRepositoryEntityRepository repoRepository;

    private GitLocalCollector collector;

    private static final Long REPO_ID = 42L;

    @TempDir
    Path repoPath;

    private RevCommit rootCommit;    // c1: creates a.txt (2 lines)
    private RevCommit secondCommit;  // c2: appends 1 line to a.txt
    private RevCommit thirdCommit;   // c3: adds b.txt (2 lines)

    @BeforeEach
    void setUp() {
        collector = new GitLocalCollector(commitRepository, repoRepository);
    }

    private void buildThreeCommitRepo() throws Exception {
        try (Git git = Git.init().setDirectory(repoPath.toFile()).call()) {
            Path a = repoPath.resolve("a.txt");
            Files.writeString(a, "line1\nline2\n");
            git.add().addFilepattern("a.txt").call();
            rootCommit = git.commit().setMessage("root").setAuthor("Root", "root@example.com").call();

            Files.writeString(a, "line1\nline2\nline3\n");
            git.add().addFilepattern("a.txt").call();
            secondCommit = git.commit().setMessage("append line").setAuthor("Alice", "alice@example.com").call();

            Files.writeString(repoPath.resolve("b.txt"), "x\ny\n");
            git.add().addFilepattern("b.txt").call();
            thirdCommit = git.commit().setMessage("add b").setAuthor("Bob", "bob@example.com").call();
        }
    }

    private GitRepositoryEntity dbRepoPointingAtTempDir() {
        DataSourceConfig ds = new DataSourceConfig();
        ds.setId(7L);

        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(REPO_ID);
        repo.setName("local-repo");
        repo.setLocalPath(repoPath.toString());
        repo.setDataSourceConfig(ds);
        return repo;
    }

    @SuppressWarnings("unchecked")
    private List<GitCommitEntity> captureSavedBatch() {
        ArgumentCaptor<List<GitCommitEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(commitRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void collectForRepository_freshRepo_collectsPendingCommitsWithComputedStats() throws Exception {
        buildThreeCommitRepo();
        GitRepositoryEntity dbRepo = dbRepoPointingAtTempDir();
        when(repoRepository.findByIdWithDataSourceConfig(REPO_ID)).thenReturn(Optional.of(dbRepo));
        // Root already stored → only the two child commits are pending.
        when(commitRepository.findHashesByRepositoryId(REPO_ID)).thenReturn(List.of(rootCommit.getName()));

        int collected = collector.collectForRepository(REPO_ID, null);

        assertThat(collected).isEqualTo(2);

        List<GitCommitEntity> saved = captureSavedBatch();
        assertThat(saved).extracting(GitCommitEntity::getHash)
                .containsExactlyInAnyOrder(secondCommit.getName(), thirdCommit.getName());

        GitCommitEntity appendCommit = saved.stream()
                .filter(c -> c.getHash().equals(secondCommit.getName())).findFirst().orElseThrow();
        assertThat(appendCommit.getAuthorName()).isEqualTo("Alice");
        assertThat(appendCommit.getAuthorEmail()).isEqualTo("alice@example.com");
        assertThat(appendCommit.getMessage()).isEqualTo("append line");
        assertThat(appendCommit.getParentHash()).isEqualTo(rootCommit.getName());
        assertThat(appendCommit.getAdditions()).isEqualTo(1);   // one appended line
        assertThat(appendCommit.getDeletions()).isZero();
        assertThat(appendCommit.getFilesChanged()).isEqualTo(1);
        assertThat(appendCommit.getRepository()).isSameAs(dbRepo);

        GitCommitEntity addFileCommit = saved.stream()
                .filter(c -> c.getHash().equals(thirdCommit.getName())).findFirst().orElseThrow();
        assertThat(addFileCommit.getAuthorName()).isEqualTo("Bob");
        assertThat(addFileCommit.getParentHash()).isEqualTo(secondCommit.getName());
        assertThat(addFileCommit.getAdditions()).isEqualTo(2);  // new file, two lines
        assertThat(addFileCommit.getFilesChanged()).isEqualTo(1);
    }

    @Test
    void collectForRepository_rootCommit_countsWholeFileAsAdditions() throws Exception {
        buildThreeCommitRepo();
        GitRepositoryEntity dbRepo = dbRepoPointingAtTempDir();
        when(repoRepository.findByIdWithDataSourceConfig(REPO_ID)).thenReturn(Optional.of(dbRepo));
        when(commitRepository.findHashesByRepositoryId(REPO_ID)).thenReturn(List.of());

        int collected = collector.collectForRepository(REPO_ID, null);

        assertThat(collected).isEqualTo(3);
        GitCommitEntity root = captureSavedBatch().stream()
                .filter(c -> c.getHash().equals(rootCommit.getName())).findFirst().orElseThrow();
        // Root commit is diffed against the empty tree: every line of the new file counts as an addition.
        assertThat(root.getParentHash()).isNull();
        assertThat(root.getAdditions()).isEqualTo(2);   // a.txt: line1, line2
        assertThat(root.getDeletions()).isZero();
        assertThat(root.getFilesChanged()).isEqualTo(1);
    }

    @Test
    void collectForRepository_freshRepo_advancesBookkeepingToNewestCommit() throws Exception {
        buildThreeCommitRepo();
        GitRepositoryEntity dbRepo = dbRepoPointingAtTempDir();
        when(repoRepository.findByIdWithDataSourceConfig(REPO_ID)).thenReturn(Optional.of(dbRepo));
        when(commitRepository.findHashesByRepositoryId(REPO_ID)).thenReturn(List.of(rootCommit.getName()));

        collector.collectForRepository(REPO_ID, null);

        assertThat(dbRepo.getLastFetchedCommitHash()).isEqualTo(thirdCommit.getName());
        assertThat(dbRepo.getLastScanAt()).isNotNull();
        assertThat(dbRepo.getDataSourceConfig().getLastSuccessSync()).isNotNull();
        verify(repoRepository).save(dbRepo);
    }

    @Test
    void collectForRepository_withJobState_tracksPhaseProgress() throws Exception {
        buildThreeCommitRepo();
        GitRepositoryEntity dbRepo = dbRepoPointingAtTempDir();
        when(repoRepository.findByIdWithDataSourceConfig(REPO_ID)).thenReturn(Optional.of(dbRepo));
        when(commitRepository.findHashesByRepositoryId(REPO_ID)).thenReturn(List.of(rootCommit.getName()));
        SyncJobTracker.JobState jobState = new SyncJobTracker.JobState();

        collector.collectForRepository(REPO_ID, jobState);

        assertThat(jobState.phaseTotal).isEqualTo(2);
        assertThat(jobState.phaseProcessed.get()).isEqualTo(2);
        assertThat(jobState.totalProcessed.get()).isEqualTo(2);
    }

    // ── incremental / no-op paths ───────────────────────────────────────────────

    @Test
    void collectForRepository_lastFetchedHashSet_stopsWalkAtThatCommit() throws Exception {
        buildThreeCommitRepo();
        GitRepositoryEntity dbRepo = dbRepoPointingAtTempDir();
        dbRepo.setLastFetchedCommitHash(secondCommit.getName());   // walk stops here
        when(repoRepository.findByIdWithDataSourceConfig(REPO_ID)).thenReturn(Optional.of(dbRepo));
        when(commitRepository.findHashesByRepositoryId(REPO_ID)).thenReturn(List.of());

        int collected = collector.collectForRepository(REPO_ID, null);

        assertThat(collected).isEqualTo(1);
        assertThat(captureSavedBatch()).extracting(GitCommitEntity::getHash)
                .containsExactly(thirdCommit.getName());
        assertThat(dbRepo.getLastScanAt()).isNotNull();
    }

    @Test
    void collectForRepository_allHashesAlreadyPersisted_returnsZeroAndTouchesScanTime() throws Exception {
        buildThreeCommitRepo();
        GitRepositoryEntity dbRepo = dbRepoPointingAtTempDir();
        when(repoRepository.findByIdWithDataSourceConfig(REPO_ID)).thenReturn(Optional.of(dbRepo));
        when(commitRepository.findHashesByRepositoryId(REPO_ID))
                .thenReturn(List.of(rootCommit.getName(), secondCommit.getName(), thirdCommit.getName()));

        int collected = collector.collectForRepository(REPO_ID, null);

        assertThat(collected).isZero();
        assertThat(dbRepo.getLastScanAt()).isNotNull();
        verify(repoRepository).save(dbRepo);
        verify(commitRepository, never()).saveAll(any());
    }

    // ── error paths ─────────────────────────────────────────────────────────────

    @Test
    void collectForRepository_repoNotFound_throwsNoSuchElement() {
        when(repoRepository.findByIdWithDataSourceConfig(REPO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> collector.collectForRepository(REPO_ID, null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Git repo not found: " + REPO_ID);
    }

    @Test
    void collectForRepository_localPathMissing_throwsNoSuchElement() {
        GitRepositoryEntity dbRepo = dbRepoPointingAtTempDir();
        dbRepo.setLocalPath(new File(repoPath.toFile(), "does-not-exist").getAbsolutePath());
        when(repoRepository.findByIdWithDataSourceConfig(REPO_ID)).thenReturn(Optional.of(dbRepo));

        assertThatThrownBy(() -> collector.collectForRepository(REPO_ID, null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Local repo path does not exist");
    }

    @Test
    void collectForRepository_directoryIsNotAGitRepo_throwsGitException() {
        // repoPath exists (a real @TempDir) but was never `git init`-ed → Git.open fails.
        GitRepositoryEntity dbRepo = dbRepoPointingAtTempDir();
        when(repoRepository.findByIdWithDataSourceConfig(REPO_ID)).thenReturn(Optional.of(dbRepo));

        assertThatThrownBy(() -> collector.collectForRepository(REPO_ID, null))
                .isInstanceOf(GitException.class)
                .hasMessageContaining("Failed to collect git commits");
    }
}
