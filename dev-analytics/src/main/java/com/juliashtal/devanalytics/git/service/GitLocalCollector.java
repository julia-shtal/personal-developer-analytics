package com.juliashtal.devanalytics.git.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.exception.GitException;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Collects commits from a local Git working copy via JGit, batching diffs across worker threads.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitLocalCollector {

    private static final int BATCH_SIZE = 500;
    private static final int DIFF_THREADS = Math.min(Runtime.getRuntime().availableProcessors(), 4);

    private final GitCommitEntityRepository commitRepository;
    private final GitRepositoryEntityRepository repoRepository;

    /**
     * Collects commits for the specified local repository.
     * If lastFetchedCommitHash == null, we take the entire history.
     * Otherwise, only new commits on top of the saved ones.
     *
     * Not @Transactional — each commitRepository.saveAll() call is its own short transaction,
     * so we never hold a single DB connection open for the entire (potentially long) job.
     */
    public int collectForRepository(Long repoId, SyncJobTracker.JobState jobState) {
        // JOIN FETCH loads dataSourceConfig eagerly so it is available after the session closes.
        GitRepositoryEntity dbRepo = repoRepository.findByIdWithDataSourceConfig(repoId)
                .orElseThrow(() -> new NoSuchElementException("Git repo not found: " + repoId));

        log.info("Collecting commits from local repo: id={}, path={}", repoId, dbRepo.getLocalPath());

        File repoDir = new File(dbRepo.getLocalPath());
        if (!repoDir.exists()) {
            throw new NoSuchElementException("Local repo path does not exist: " + dbRepo.getLocalPath());
        }

        try (Git git = Git.open(repoDir)) {
            Repository repository = git.getRepository();

            // Load all existing hashes in one query to avoid per-commit DB lookups.
            Set<String> existingHashes = new HashSet<>(
                    commitRepository.findHashesByRepositoryId(dbRepo.getId()));

            PendingCommits pendingCommits = collectPendingMetadata(git, dbRepo, existingHashes);
            List<CommitMeta> pending = pendingCommits.metas();

            if (pending.isEmpty()) {
                log.debug("No new commits for local repo id={}", repoId);
                dbRepo.setLastScanAt(Instant.now());
                repoRepository.save(dbRepo);
                return 0;
            }

            // Now that we know exactly how many commits are pending, tell the tracker
            // so it can calculate an accurate ETA for the diff-computation phase.
            if (jobState != null) {
                jobState.phaseTotal = pending.size();
            }

            computeDiffsAndSave(repository, pending, dbRepo, jobState);

            updateRepoAfterCollection(dbRepo, pendingCommits.newestHash());
            log.info("Collected {} new commits from local repo id={}", pending.size(), repoId);
            return pending.size();

        } catch (IOException | GitAPIException e) {
            throw new GitException("Failed to collect git commits from " + dbRepo.getLocalPath(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitException("Diff computation interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioe) {
                throw new GitException("Diff computation failed", ioe);
            }
            throw new GitException("Diff computation failed: " + cause.getMessage(), cause);
        }
    }

    /**
     * Phase 1: fast single-threaded pass over the commit log — collects metadata only.
     * No diff computation here; that is the expensive part, done in {@link #computeDiffsAndSave}.
     * Stops at the last fetched commit and skips hashes already present in the database.
     */
    private PendingCommits collectPendingMetadata(Git git, GitRepositoryEntity dbRepo, Set<String> existingHashes)
            throws IOException, GitAPIException {
        List<CommitMeta> pending = new ArrayList<>();
        String newestHash = dbRepo.getLastFetchedCommitHash();

        for (RevCommit commit : git.log().call()) {
            String hash = commit.getName();

            // Stop at the last commit we already fetched.
            if (hash.equals(dbRepo.getLastFetchedCommitHash())) break;
            if (existingHashes.contains(hash)) continue;

            // First commit in log order is the newest.
            if (newestHash == null) newestHash = hash;

            pending.add(new CommitMeta(
                    commit.getId(),
                    hash,
                    commit.getAuthorIdent().getName(),
                    commit.getAuthorIdent().getEmailAddress(),
                    commit.getAuthorIdent().getWhenAsInstant(),
                    commit.getFullMessage(),
                    commit.getParentCount() > 0 ? commit.getParent(0).getName() : null
            ));
        }

        return new PendingCommits(pending, newestHash);
    }

    /**
     * Phase 2: parallel diff computation and batched save.
     * Each thread owns its ObjectReader / RevWalk / DiffFormatter —
     * those JGit objects are not thread-safe and must not be shared.
     */
    private void computeDiffsAndSave(Repository repository, List<CommitMeta> pending,
                                      GitRepositoryEntity dbRepo, SyncJobTracker.JobState jobState)
            throws InterruptedException, ExecutionException {
        ExecutorService diffPool = Executors.newFixedThreadPool(
                DIFF_THREADS,
                r -> {
                    Thread t = new Thread(r, "git-diff");
                    t.setDaemon(true);
                    return t;
                });

        try {
            List<Future<GitCommitEntity>> futures = pending.stream()
                    .map(meta -> diffPool.submit(() -> buildEntity(repository, meta, dbRepo)))
                    .toList();

            List<GitCommitEntity> batch = new ArrayList<>(BATCH_SIZE);
            for (Future<GitCommitEntity> future : futures) {
                batch.add(future.get());
                // Increment per-commit so the ETA calculation has a smooth rate signal.
                if (jobState != null) {
                    jobState.phaseProcessed.incrementAndGet();
                    jobState.totalProcessed.incrementAndGet();
                }
                if (batch.size() >= BATCH_SIZE) {
                    // Each saveAll runs in its own short transaction (SimpleJpaRepository is @Transactional).
                    commitRepository.saveAll(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                commitRepository.saveAll(batch);
            }
        } finally {
            diffPool.shutdown();
        }
    }

    /**
     * Updates the repo's sync bookkeeping (last fetched hash, scan time, data source sync time)
     * after a successful collection run.
     */
    private void updateRepoAfterCollection(GitRepositoryEntity dbRepo, String newestHash) {
        if (newestHash != null && !newestHash.equals(dbRepo.getLastFetchedCommitHash())) {
            dbRepo.setLastFetchedCommitHash(newestHash);
        }
        dbRepo.setLastScanAt(Instant.now());

        DataSourceConfig cfg = dbRepo.getDataSourceConfig();
        cfg.setLastSuccessSync(Instant.now());

        repoRepository.save(dbRepo);
    }

    /**
     * Builds a GitCommitEntity from pre-extracted metadata and computes diff stats.
     * Creates its own JGit reader/walker/formatter since those objects are not thread-safe.
     */
    private GitCommitEntity buildEntity(Repository repository, CommitMeta meta,
                                        GitRepositoryEntity dbRepo) throws IOException {
        try (ObjectReader reader = repository.newObjectReader();
             RevWalk revWalk = new RevWalk(repository);
             DiffFormatter diffFormatter = new DiffFormatter(OutputStream.nullOutputStream())) {

            diffFormatter.setRepository(repository);
            diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
            diffFormatter.setDetectRenames(true);

            RevCommit commit = revWalk.parseCommit(meta.commitId());
            DiffStats stats = calculateDiffStats(commit, reader, revWalk, diffFormatter);

            GitCommitEntity entity = new GitCommitEntity();
            entity.setRepository(dbRepo);
            entity.setHash(meta.hash());
            entity.setAuthorName(meta.authorName());
            entity.setAuthorEmail(meta.authorEmail());
            entity.setAuthorDate(meta.authorDate());
            entity.setMessage(meta.message());
            entity.setParentHash(meta.parentHash());
            entity.setAdditions(stats.additions());
            entity.setDeletions(stats.deletions());
            entity.setFilesChanged(stats.filesChanged());
            return entity;
        }
    }

    /**
     * Calculates additions/deletions/filesChanged for a commit.
     * The reader, revWalk, and diffFormatter must all be owned by the calling thread.
     */
    private DiffStats calculateDiffStats(RevCommit commit,
                                         ObjectReader reader, RevWalk revWalk,
                                         DiffFormatter diffFormatter) throws IOException {
        AbstractTreeIterator oldTreeIter;
        if (commit.getParentCount() == 0) {
            // Root commit: diff against the empty tree. JGit cannot open ObjectId.zeroId()
            // as a real tree object, so an EmptyTreeIterator is required — resetting a
            // CanonicalTreeParser to zeroId throws MissingObjectException.
            oldTreeIter = new EmptyTreeIterator();
        } else {
            RevCommit parent = commit.getParent(0);
            ObjectId oldTreeId = revWalk.parseCommit(parent.getId()).getTree().getId();
            CanonicalTreeParser parentTreeIter = new CanonicalTreeParser();
            parentTreeIter.reset(reader, oldTreeId);
            oldTreeIter = parentTreeIter;
        }

        CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
        newTreeIter.reset(reader, commit.getTree().getId());

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

    private record CommitMeta(ObjectId commitId, String hash, String authorName, String authorEmail,
                               Instant authorDate, String message, String parentHash) {}

    private record PendingCommits(List<CommitMeta> metas, String newestHash) {}

    private record DiffStats(int additions, int deletions, int filesChanged) {}
}
