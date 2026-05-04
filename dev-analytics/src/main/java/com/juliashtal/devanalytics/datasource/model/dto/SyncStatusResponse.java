package com.juliashtal.devanalytics.datasource.model.dto;

import java.util.List;

/**
 * Snapshot of a running (or recently finished) collection job.
 *
 * @param running          true while the background thread is still working
 * @param phaseNumber      1-based index of the current phase
 * @param totalPhases      total number of phases for this job type
 * @param phase            human-readable phase label, e.g. "commits", "pull requests"
 * @param phaseProcessed   items saved in the current phase
 * @param phaseTotal       estimated total items in this phase (–1 = unknown)
 * @param totalProcessed   cumulative items saved across all phases
 * @param elapsedSeconds   seconds since the job started
 * @param phaseEtaSeconds  estimated seconds left in the current phase (null = unknown)
 * @param overallEtaSeconds estimated seconds left for the whole job (null = unknown)
 * @param completedPhases  summary of phases already finished
 * @param result           final summary message when running=false and no error
 * @param error            error message when the job failed
 */
public record SyncStatusResponse(
        boolean running,
        int phaseNumber,
        int totalPhases,
        String phase,
        int phaseProcessed,
        int phaseTotal,
        int totalProcessed,
        long elapsedSeconds,
        Long phaseEtaSeconds,
        Long overallEtaSeconds,
        List<CompletedPhase> completedPhases,
        String result,
        String error
) {
    public record CompletedPhase(String name, int itemsSaved, long durationSeconds) {}
}
