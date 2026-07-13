package com.juliashtal.devanalytics.datasource.service;

import com.juliashtal.devanalytics.datasource.model.SyncJobEntity;
import com.juliashtal.devanalytics.datasource.model.SyncJobStatus;
import com.juliashtal.devanalytics.datasource.repository.SyncJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory registry of active and recently-completed collection jobs.
 * Thread-safe: multiple collectors may update the same job concurrently.
 * Each state change is also persisted to {@code sync_jobs} so the record
 * survives a restart and the status endpoint can fall back to the DB.
 */
@Component
@RequiredArgsConstructor
public class SyncJobTracker {

    private final SyncJobRepository syncJobRepository;

    /** Summary of a completed phase, stored for the final result display. */
    public record PhaseSummary(String name, int itemsSaved, long durationSeconds) {}

    /**
     * Mutable in-memory progress state for one running sync job.
     */
    public static class JobState {
        public volatile boolean running = true;
        public final Instant startedAt = Instant.now();
        /** DB primary key of the persisted sync_jobs row — set by {@link SyncJobTracker#start}. */
        public volatile Long syncJobId;

        // Phase tracking
        public volatile int phaseNumber = 0;
        public volatile int totalPhases = 1;
        public volatile String phase = "starting";
        public volatile Instant phaseStartedAt = Instant.now();

        // Per-phase counters (reset each phase)
        public final AtomicInteger phaseProcessed = new AtomicInteger(0);
        /** Total items expected in the current phase (–1 = unknown). */
        public volatile int phaseTotal = -1;

        // Cumulative across all phases
        public final AtomicInteger totalProcessed = new AtomicInteger(0);

        // Completed-phase history, for the overall summary
        public final List<PhaseSummary> completedPhases =
                Collections.synchronizedList(new ArrayList<>());

        // Completion state
        public volatile Instant completedAt;
        public volatile String result;
        public volatile String error;
    }

    private final ConcurrentHashMap<Long, JobState> jobs = new ConcurrentHashMap<>();

    /** Called once when an async sync job begins. Also creates the persisted DB record. */
    public JobState start(Long dataSourceId) {
        JobState state = new JobState();
        jobs.put(dataSourceId, state);
        try {
            SyncJobEntity entity = new SyncJobEntity();
            entity.setDataSourceId(dataSourceId);
            entity.setStatus(SyncJobStatus.RUNNING);
            entity.setPhase("starting");
            entity.setStartedAt(state.startedAt);
            SyncJobEntity saved = syncJobRepository.save(entity);
            state.syncJobId = saved.getId();
        } catch (Exception ignored) {
            // DB write failure must never block the actual sync job
        }
        return state;
    }

    public Optional<JobState> getState(Long dataSourceId) {
        return Optional.ofNullable(jobs.get(dataSourceId));
    }

    /** Most recent persisted sync job for a data source — fallback when no in-memory state exists (e.g. after a restart). */
    public Optional<SyncJobEntity> findLatestPersisted(Long dataSourceId) {
        return syncJobRepository.findTopByDataSourceIdOrderByStartedAtDesc(dataSourceId);
    }

    /**
     * Advance to a new named phase. The previous phase (if any) is archived
     * in {@code completedPhases} so the UI can show a history.
     */
    public void setPhase(JobState state, String phaseName, int phaseTotalEstimate) {
        // Archive the phase that just finished
        if (state.phaseNumber > 0 && state.phaseProcessed.get() > 0) {
            long dur = Duration.between(state.phaseStartedAt, Instant.now()).getSeconds();
            state.completedPhases.add(
                    new PhaseSummary(state.phase, state.phaseProcessed.get(), dur));
        }
        state.phaseNumber++;
        state.phase = phaseName;
        state.phaseTotal = phaseTotalEstimate;
        state.phaseStartedAt = Instant.now();
        state.phaseProcessed.set(0);
        if (state.syncJobId != null) {
            try {
                syncJobRepository.updatePhase(state.syncJobId, phaseName);
            } catch (Exception ignored) {}
        }
    }

    /** Record that {@code delta} more items have been saved in the current phase. */
    public void addProgress(JobState state, int delta) {
        state.phaseProcessed.addAndGet(delta);
        state.totalProcessed.addAndGet(delta);
    }

    /**
     * Estimated seconds remaining in the CURRENT phase, or null if not calculable.
     * Rate is derived from time elapsed since the current phase started.
     */
    public Long phaseEtaSeconds(JobState state) {
        if (state.phaseTotal <= 0) return null;
        int processed = state.phaseProcessed.get();
        if (processed <= 0) return null;
        long elapsed = Duration.between(state.phaseStartedAt, Instant.now()).getSeconds();
        if (elapsed <= 0) return null;
        double rate = (double) processed / elapsed;
        long remaining = state.phaseTotal - processed;
        if (remaining <= 0) return 0L;
        return (long) Math.ceil(remaining / rate);
    }

    /**
     * Estimated total seconds remaining for the WHOLE job.
     * For the current phase we use the phase rate if total is known.
     * Future phases (those without estimates) contribute an unknown amount,
     * so we return null unless we can estimate at least the current phase.
     */
    public Long overallEtaSeconds(JobState state) {
        Long current = phaseEtaSeconds(state);
        // We can only give a meaningful overall ETA when we know how long the
        // current (usually the longest) phase has remaining.
        return current;
    }

    public void complete(Long dataSourceId, String result) {
        JobState state = jobs.get(dataSourceId);
        if (state != null) {
            // Archive the last phase
            if (state.phaseProcessed.get() > 0) {
                long dur = Duration.between(state.phaseStartedAt, Instant.now()).getSeconds();
                state.completedPhases.add(
                        new PhaseSummary(state.phase, state.phaseProcessed.get(), dur));
            }
            state.running = false;
            state.completedAt = Instant.now();
            state.result = result;
            if (state.syncJobId != null) {
                try {
                    syncJobRepository.markCompleted(
                            state.syncJobId, SyncJobStatus.COMPLETED, state.completedAt,
                            result, state.totalProcessed.get());
                } catch (Exception ignored) {}
            }
        }
    }

    public void fail(Long dataSourceId, String error) {
        JobState state = jobs.get(dataSourceId);
        if (state != null) {
            state.running = false;
            state.completedAt = Instant.now();
            state.error = error;
            if (state.syncJobId != null) {
                try {
                    syncJobRepository.markFailed(
                            state.syncJobId, SyncJobStatus.FAILED, state.completedAt, error);
                } catch (Exception ignored) {}
            }
        }
    }

    /** Drop completed jobs older than 1 hour to avoid memory leaks. */
    @Scheduled(fixedRate = 3_600_000)
    public void cleanup() {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        jobs.entrySet().removeIf(e ->
                !e.getValue().running
                && e.getValue().completedAt != null
                && e.getValue().completedAt.isBefore(cutoff));
    }
}
