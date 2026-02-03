package com.juliashtal.devanalytics.git.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.exception.GitException;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class GitLocalCollector {

    private final GitCommitEntityRepository commitRepository;
    private final GitRepositoryEntityRepository repoRepository;

    public GitLocalCollector(GitCommitEntityRepository commitRepository,
                             GitRepositoryEntityRepository repoRepository) {
        this.commitRepository = commitRepository;
        this.repoRepository = repoRepository;
    }

    /**
     * Collects commits for the specified local repository.
     * If lastFetchedCommitHash == null, we take the entire history.
     * Otherwise, only new commits on top of the saved ones.
     */
    @Transactional
    public int collectForRepository(Long repoId) {
        GitRepositoryEntity dbRepo = repoRepository.findById(repoId)
                .orElseThrow(() -> new NoSuchElementException("Git repo not found: " + repoId));

        File repoDir = new File(dbRepo.getLocalPath());
        if (!repoDir.exists()) {
            throw new NoSuchElementException("Local repo path does not exist: " + dbRepo.getLocalPath());
        }

        try (Git git = Git.open(repoDir)) {
            Repository repository = git.getRepository();

            Iterable<RevCommit> log = git.log().call();
            int saved = 0;
            String newestHash = dbRepo.getLastFetchedCommitHash();

            for (RevCommit commit : log) {
                String hash = commit.getName();

                // if we have already saved this commit — then all the old ones; we can interrupt
                if (hash.equals(dbRepo.getLastFetchedCommitHash())) {
                    break;
                }

                // if it already exists in the database (just in case)
                if (commitRepository.findByHash(hash).isPresent()) {
                    continue;
                }

                GitCommitEntity entity = mapCommit(repository, commit, dbRepo);
                commitRepository.save(entity);
                saved++;

                if (newestHash == null) {
                    newestHash = hash;
                }
            }

            if (newestHash != null && !newestHash.equals(dbRepo.getLastFetchedCommitHash())) {
                dbRepo.setLastFetchedCommitHash(newestHash);
            }
            dbRepo.setLastScanAt(LocalDateTime.now());

            DataSourceConfig cfg = dbRepo.getDataSourceConfig();
            cfg.setLastSuccessSync(LocalDateTime.now());

            repoRepository.save(dbRepo);

            return saved;

        } catch (IOException | GitAPIException e) {
            throw new GitException("Failed to collect git commits from " + dbRepo.getLocalPath(), e);
        }
    }

    private GitCommitEntity mapCommit(Repository repository, RevCommit commit, GitRepositoryEntity dbRepo) throws IOException {
        GitCommitEntity entity = new GitCommitEntity();
        entity.setRepository(dbRepo);
        entity.setHash(commit.getName());
        entity.setAuthorName(commit.getAuthorIdent().getName());
        entity.setAuthorEmail(commit.getAuthorIdent().getEmailAddress());
        entity.setAuthorDate(commit.getAuthorIdent().getWhenAsInstant());
        entity.setMessage(commit.getFullMessage());

        // parent
        if (commit.getParentCount() > 0) {
            entity.setParentHash(commit.getParent(0).getName());
        }

        // diff metrics (additions, deletions, filesChanged)
        DiffStats stats = calculateDiffStats(repository, commit);
        entity.setAdditions(stats.additions());
        entity.setDeletions(stats.deletions());
        entity.setFilesChanged(stats.filesChanged());

        return entity;
    }

    /**
     * Simple calculation of additions/deletions/filesChanged via DiffFormatter.
     */
    private DiffStats calculateDiffStats(Repository repository, RevCommit commit) throws IOException {
        if (commit.getParentCount() == 0) {
            // initial commit: count everything as additions
            // we can simply count the number of lines as additions, but for MVP we will set it to 0
            return new DiffStats(0, 0, 0);
        }

        RevCommit parent = commit.getParent(0);

        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit parentCommit = walk.parseCommit(parent.getId());
            RevCommit thisCommit = walk.parseCommit(commit.getId());

            ObjectId oldTree = parentCommit.getTree().getId();
            ObjectId newTree = thisCommit.getTree().getId();

            try (ObjectReader reader = repository.newObjectReader()) {
                CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                oldTreeIter.reset(reader, oldTree);

                CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                newTreeIter.reset(reader, newTree);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (DiffFormatter diffFormatter = new DiffFormatter(out)) {
                    diffFormatter.setRepository(repository);
                    diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
                    diffFormatter.setDetectRenames(true);

                    List<DiffEntry> diffs = diffFormatter.scan(oldTreeIter, newTreeIter);

                    int filesChanged = diffs.size();
                    int additions = 0;
                    int deletions = 0;

                    for (DiffEntry diff : diffs) {
                        diffFormatter.format(diff);
                        EditList edits = diffFormatter.toFileHeader(diff).toEditList();
                        for (Edit edit : edits) {
                            additions += edit.getEndB() - edit.getBeginB();
                            deletions += edit.getEndA() - edit.getBeginA();
                        }
                    }
                    return new DiffStats(additions, deletions, filesChanged);
                }
            }
        }
    }

    private record DiffStats(int additions, int deletions, int filesChanged) {}
}

