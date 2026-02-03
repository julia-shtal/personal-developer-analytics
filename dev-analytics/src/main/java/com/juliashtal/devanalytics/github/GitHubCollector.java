package com.juliashtal.devanalytics.github;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.exception.GitHubException;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Service
public class GitHubCollector {

    private final GitRepositoryEntityRepository repoRepository;
    private final GitCommitEntityRepository commitRepository;
    private final GitHubClientFactory clientFactory;

    public GitHubCollector(GitRepositoryEntityRepository repoRepository,
                           GitCommitEntityRepository commitRepository,
                           GitHubClientFactory clientFactory) {
        this.repoRepository = repoRepository;
        this.commitRepository = commitRepository;
        this.clientFactory = clientFactory;
    }

    @Transactional
    public int collectForRepository(Long gitRepoId) {
        GitRepositoryEntity repo = repoRepository.findById(gitRepoId)
                .orElseThrow(() -> new NoSuchElementException("Git repo not found: " + gitRepoId));

        DataSourceConfig cfg = repo.getDataSourceConfig();
        GitHub github = clientFactory.createClient(cfg);

        try {
            GHRepository ghRepo = github.getRepository(repo.getName()); // "owner/repo"

            // List of commits (hub4j returns Iterable)
            Iterable<GHCommit> commits = ghRepo.listCommits();

            String lastFetched = repo.getLastFetchedCommitHash();
            int saved = 0;
            String newestHash = lastFetched;

            for (GHCommit ghCommit : commits) {
                String hash = ghCommit.getSHA1();

                // if we have already reached the last saved commit — stop
                if (lastFetched != null && lastFetched.equals(hash)) {
                    break;
                }

                if (commitRepository.findByHash(hash).isPresent()) {
                    continue;
                }

                GitCommitEntity entity = mapCommit(ghCommit, repo);
                commitRepository.save(entity);
                saved++;

                if (newestHash == null) {
                    newestHash = hash;
                }
            }

            if (newestHash != null && !newestHash.equals(lastFetched)) {
                repo.setLastFetchedCommitHash(newestHash);
            }
            repo.setLastScanAt(LocalDateTime.now());
            cfg.setLastSuccessSync(LocalDateTime.now());
            repoRepository.save(repo);

            return saved;
        } catch (IOException e) {
            throw new GitHubException("Failed to collect GitHub commits for " + repo.getName(), e);
        }
    }

    private GitCommitEntity mapCommit(GHCommit ghCommit, GitRepositoryEntity repo) throws IOException {
        GitCommitEntity entity = new GitCommitEntity();
        entity.setRepository(repo);
        entity.setHash(ghCommit.getSHA1());

        GHCommit.ShortInfo info = ghCommit.getCommitShortInfo();
        if (info != null) {
            entity.setAuthorName(info.getAuthor().getName());
            entity.setAuthorEmail(info.getAuthor().getEmail());
            entity.setAuthorDate(info.getAuthoredDate().toInstant());
            entity.setMessage(info.getMessage());
        } else {
            entity.setAuthorName(
                    ghCommit.getAuthor() != null ? ghCommit.getAuthor().getName() : "unknown"
            );
            entity.setAuthorEmail(
                    ghCommit.getAuthor() != null ? ghCommit.getAuthor().getEmail() : "unknown"
            );
            entity.setAuthorDate(Instant.now());
            entity.setMessage(null);
        }

        try {
            int additions = ghCommit.getLinesAdded();     // call populate() и take stats.additions
            int deletions = ghCommit.getLinesDeleted();   // stats.deletions
            int total = ghCommit.getLinesChanged();       // stats.total

            entity.setAdditions(additions);
            entity.setDeletions(deletions);
            entity.setFilesChanged(total);
        } catch (IOException e) {
            entity.setAdditions(0);
            entity.setDeletions(0);
            entity.setFilesChanged(0);
        }

        var parents = ghCommit.getParents();
        if (parents != null && !parents.isEmpty()) {
            entity.setParentHash(parents.get(0).getSHA1());
        }

        return entity;
    }

}
