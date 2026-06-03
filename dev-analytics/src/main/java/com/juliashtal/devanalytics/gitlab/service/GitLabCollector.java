package com.juliashtal.devanalytics.gitlab.service;

import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates GitLab commit ingestion for a single repository.
 *
 * <p>Commits are ingested with stats inline ({@code with_stats=true}) so
 * no background enrichment phase is needed. Merge Requests are handled
 * separately by {@link GitLabMrIngestService}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitLabCollector {

    private final GitLabCommitIngestService commitIngestService;

    public int collectForRepository(Long gitRepoId, SyncJobTracker.JobState jobState) {
        int commits = commitIngestService.ingestForRepository(gitRepoId, jobState);
        log.debug("GitLab commit collection complete: repo={}, commits={}", gitRepoId, commits);
        return commits;
    }
}
