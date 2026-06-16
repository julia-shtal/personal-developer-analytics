package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.datasource.model.SyncJobEntity;
import com.juliashtal.devanalytics.datasource.model.SyncJobStatus;
import com.juliashtal.devanalytics.datasource.repository.SyncJobRepository;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker.JobState;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker.PhaseSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncJobTrackerTest {

    @Mock SyncJobRepository syncJobRepository;
    @InjectMocks SyncJobTracker tracker;

    @Test
    void findLatestPersisted_delegatesToRepository() {
        SyncJobEntity entity = new SyncJobEntity();
        entity.setId(42L);
        entity.setDataSourceId(7L);
        when(syncJobRepository.findTopByDataSourceIdOrderByStartedAtDesc(7L))
                .thenReturn(Optional.of(entity));

        Optional<SyncJobEntity> result = tracker.findLatestPersisted(7L);

        assertThat(result).contains(entity);
    }

    @Test
    void findLatestPersisted_noJobs_returnsEmpty() {
        when(syncJobRepository.findTopByDataSourceIdOrderByStartedAtDesc(7L))
                .thenReturn(Optional.empty());

        assertThat(tracker.findLatestPersisted(7L)).isEmpty();
    }

    // ── start ─────────────────────────────────────────────────────────────

    @Test
    void start_persistsSyncJobAndSetsSyncJobId() {
        SyncJobEntity saved = new SyncJobEntity();
        saved.setId(123L);
        when(syncJobRepository.save(any(SyncJobEntity.class))).thenReturn(saved);

        JobState state = tracker.start(7L);

        assertThat(state.running).isTrue();
        assertThat(state.phase).isEqualTo("starting");
        assertThat(state.phaseNumber).isEqualTo(0);
        assertThat(state.totalPhases).isEqualTo(1);
        assertThat(state.phaseTotal).isEqualTo(-1);
        assertThat(state.phaseProcessed.get()).isZero();
        assertThat(state.totalProcessed.get()).isZero();
        assertThat(state.completedPhases).isEmpty();
        assertThat(state.syncJobId).isEqualTo(123L);
        assertThat(tracker.getState(7L)).contains(state);

        ArgumentCaptor<SyncJobEntity> captor = ArgumentCaptor.forClass(SyncJobEntity.class);
        verify(syncJobRepository).save(captor.capture());
        assertThat(captor.getValue().getDataSourceId()).isEqualTo(7L);
        assertThat(captor.getValue().getStatus()).isEqualTo(SyncJobStatus.RUNNING);
        assertThat(captor.getValue().getPhase()).isEqualTo("starting");
    }

    @Test
    void start_repositorySaveThrows_stateStillReturnedWithNullSyncJobId() {
        when(syncJobRepository.save(any(SyncJobEntity.class)))
                .thenThrow(new RuntimeException("DB unavailable"));

        JobState state = tracker.start(8L);

        assertThat(state.running).isTrue();
        assertThat(state.syncJobId).isNull();
        assertThat(tracker.getState(8L)).contains(state);
    }

    // ── getState ──────────────────────────────────────────────────────────

    @Test
    void getState_noJobStarted_returnsEmpty() {
        assertThat(tracker.getState(999L)).isEmpty();
    }

    // ── setPhase ──────────────────────────────────────────────────────────

    @Test
    void setPhase_firstPhase_noArchiveNoPersist() {
        JobState state = new JobState();
        // phaseNumber starts at 0 → archive condition (100) is false regardless of phaseProcessed.
        // syncJobId is null → persist branch (110) is false.

        tracker.setPhase(state, "collecting-commits", 50);

        assertThat(state.phaseNumber).isEqualTo(1);
        assertThat(state.phase).isEqualTo("collecting-commits");
        assertThat(state.phaseTotal).isEqualTo(50);
        assertThat(state.phaseProcessed.get()).isZero();
        assertThat(state.completedPhases).isEmpty();
        verify(syncJobRepository, never()).updatePhase(any(), any());
    }

    @Test
    void setPhase_secondPhase_archivesPreviousAndPersistsNewPhase() {
        JobState state = new JobState();
        state.syncJobId = 55L;

        tracker.setPhase(state, "collecting-commits", 50);
        tracker.addProgress(state, 50);

        tracker.setPhase(state, "enriching-stats", 10);

        assertThat(state.phaseNumber).isEqualTo(2);
        assertThat(state.phase).isEqualTo("enriching-stats");
        assertThat(state.phaseTotal).isEqualTo(10);
        assertThat(state.phaseProcessed.get()).isZero();
        assertThat(state.completedPhases).hasSize(1);

        PhaseSummary archived = state.completedPhases.get(0);
        assertThat(archived.name()).isEqualTo("collecting-commits");
        assertThat(archived.itemsSaved()).isEqualTo(50);
        assertThat(archived.durationSeconds()).isGreaterThanOrEqualTo(0L);

        verify(syncJobRepository, times(2)).updatePhase(eq(55L), anyString());
    }

    @Test
    void setPhase_persistThrows_swallowed() {
        JobState state = new JobState();
        state.syncJobId = 56L;
        doThrow(new RuntimeException("DB unavailable"))
                .when(syncJobRepository).updatePhase(eq(56L), anyString());

        tracker.setPhase(state, "collecting-commits", 50);

        assertThat(state.phase).isEqualTo("collecting-commits");
    }

    // ── addProgress ───────────────────────────────────────────────────────

    @Test
    void addProgress_incrementsPhaseAndTotalCounters() {
        JobState state = new JobState();

        tracker.addProgress(state, 5);
        tracker.addProgress(state, 3);

        assertThat(state.phaseProcessed.get()).isEqualTo(8);
        assertThat(state.totalProcessed.get()).isEqualTo(8);
    }

    // ── phaseEtaSeconds ───────────────────────────────────────────────────

    @Test
    void phaseEtaSeconds_phaseTotalUnknown_returnsNull() {
        JobState state = new JobState();
        state.phaseTotal = -1;
        state.phaseProcessed.set(10);

        assertThat(tracker.phaseEtaSeconds(state)).isNull();
    }

    @Test
    void phaseEtaSeconds_noProgressYet_returnsNull() {
        JobState state = new JobState();
        state.phaseTotal = 100;
        state.phaseProcessed.set(0);

        assertThat(tracker.phaseEtaSeconds(state)).isNull();
    }

    @Test
    void phaseEtaSeconds_noElapsedTime_returnsNull() {
        JobState state = new JobState();
        state.phaseTotal = 100;
        state.phaseProcessed.set(10);
        // phaseStartedAt defaults to "now" → elapsed seconds == 0.
        state.phaseStartedAt = Instant.now();

        assertThat(tracker.phaseEtaSeconds(state)).isNull();
    }

    @Test
    void phaseEtaSeconds_processedReachedTotal_returnsZero() {
        JobState state = new JobState();
        state.phaseTotal = 100;
        state.phaseProcessed.set(100);
        state.phaseStartedAt = Instant.now().minus(10, ChronoUnit.SECONDS);

        assertThat(tracker.phaseEtaSeconds(state)).isEqualTo(0L);
    }

    @Test
    void phaseEtaSeconds_partialProgress_returnsPositiveEstimate() {
        JobState state = new JobState();
        state.phaseTotal = 100;
        state.phaseProcessed.set(10);
        // 10 items in 10s → rate = 1/s; remaining = 90 → eta = 90s.
        state.phaseStartedAt = Instant.now().minus(10, ChronoUnit.SECONDS);

        Long eta = tracker.phaseEtaSeconds(state);

        assertThat(eta).isNotNull();
        assertThat(eta).isGreaterThan(0L);
    }

    // ── overallEtaSeconds ─────────────────────────────────────────────────

    @Test
    void overallEtaSeconds_delegatesToPhaseEtaSeconds() {
        JobState state = new JobState();
        state.phaseTotal = 100;
        state.phaseProcessed.set(10);
        state.phaseStartedAt = Instant.now().minus(10, ChronoUnit.SECONDS);

        assertThat(tracker.overallEtaSeconds(state)).isEqualTo(tracker.phaseEtaSeconds(state));
    }

    @Test
    void overallEtaSeconds_notCalculable_returnsNull() {
        JobState state = new JobState();
        state.phaseTotal = -1;

        assertThat(tracker.overallEtaSeconds(state)).isNull();
    }

    // ── complete ──────────────────────────────────────────────────────────

    @Test
    void complete_noJobStarted_doesNothing() {
        tracker.complete(111L, "done");

        assertThat(tracker.getState(111L)).isEmpty();
        verify(syncJobRepository, never()).markCompleted(any(), any(), any(), any(), any());
    }

    @Test
    void complete_withPhaseProgress_archivesPhaseAndPersists() {
        SyncJobEntity saved = new SyncJobEntity();
        saved.setId(200L);
        when(syncJobRepository.save(any(SyncJobEntity.class))).thenReturn(saved);

        JobState state = tracker.start(20L);
        tracker.setPhase(state, "collecting-commits", 10);
        tracker.addProgress(state, 10);

        tracker.complete(20L, "Synced 10 commits");

        assertThat(state.running).isFalse();
        assertThat(state.completedAt).isNotNull();
        assertThat(state.result).isEqualTo("Synced 10 commits");
        assertThat(state.completedPhases).hasSize(1);
        assertThat(state.completedPhases.get(0).itemsSaved()).isEqualTo(10);

        verify(syncJobRepository).markCompleted(
                eq(200L), eq(SyncJobStatus.COMPLETED), any(), eq("Synced 10 commits"), eq(10));
    }

    @Test
    void complete_syncJobIdNullAndNoPhaseProgress_noArchiveNoPersist() {
        when(syncJobRepository.save(any(SyncJobEntity.class)))
                .thenThrow(new RuntimeException("DB unavailable"));

        JobState state = tracker.start(99L);

        tracker.complete(99L, "done");

        assertThat(state.running).isFalse();
        assertThat(state.result).isEqualTo("done");
        assertThat(state.completedPhases).isEmpty();
        verify(syncJobRepository, never()).markCompleted(any(), any(), any(), any(), any());
    }

    @Test
    void complete_persistThrows_swallowed() {
        SyncJobEntity saved = new SyncJobEntity();
        saved.setId(201L);
        when(syncJobRepository.save(any(SyncJobEntity.class))).thenReturn(saved);
        doThrow(new RuntimeException("DB unavailable"))
                .when(syncJobRepository).markCompleted(eq(201L), any(), any(), any(), any());

        JobState state = tracker.start(21L);

        tracker.complete(21L, "done");

        assertThat(state.running).isFalse();
        assertThat(state.result).isEqualTo("done");
    }

    // ── fail ──────────────────────────────────────────────────────────────

    @Test
    void fail_noJobStarted_doesNothing() {
        tracker.fail(112L, "boom");

        assertThat(tracker.getState(112L)).isEmpty();
        verify(syncJobRepository, never()).markFailed(any(), any(), any(), any());
    }

    @Test
    void fail_withSyncJobId_persists() {
        SyncJobEntity saved = new SyncJobEntity();
        saved.setId(210L);
        when(syncJobRepository.save(any(SyncJobEntity.class))).thenReturn(saved);

        JobState state = tracker.start(22L);

        tracker.fail(22L, "GitHub API error");

        assertThat(state.running).isFalse();
        assertThat(state.completedAt).isNotNull();
        assertThat(state.error).isEqualTo("GitHub API error");
        verify(syncJobRepository).markFailed(eq(210L), eq(SyncJobStatus.FAILED), any(), eq("GitHub API error"));
    }

    @Test
    void fail_withoutSyncJobId_noPersist() {
        when(syncJobRepository.save(any(SyncJobEntity.class)))
                .thenThrow(new RuntimeException("DB unavailable"));

        JobState state = tracker.start(23L);
        // start()'s catch left syncJobId == null.

        tracker.fail(23L, "GitHub API error");

        assertThat(state.running).isFalse();
        assertThat(state.error).isEqualTo("GitHub API error");
        verify(syncJobRepository, never()).markFailed(any(), any(), any(), any());
    }

    @Test
    void fail_persistThrows_swallowed() {
        SyncJobEntity saved = new SyncJobEntity();
        saved.setId(211L);
        when(syncJobRepository.save(any(SyncJobEntity.class))).thenReturn(saved);
        doThrow(new RuntimeException("DB unavailable"))
                .when(syncJobRepository).markFailed(eq(211L), any(), any(), any());

        JobState state = tracker.start(24L);

        tracker.fail(24L, "boom");

        assertThat(state.running).isFalse();
        assertThat(state.error).isEqualTo("boom");
    }

    // ── cleanup ───────────────────────────────────────────────────────────

    @Test
    void cleanup_removesOnlyStaleCompletedJobs() {
        SyncJobEntity e1 = new SyncJobEntity(); e1.setId(301L);
        SyncJobEntity e2 = new SyncJobEntity(); e2.setId(302L);
        SyncJobEntity e3 = new SyncJobEntity(); e3.setId(303L);
        SyncJobEntity e4 = new SyncJobEntity(); e4.setId(304L);
        when(syncJobRepository.save(any(SyncJobEntity.class)))
                .thenReturn(e1, e2, e3, e4);

        // (a) still running → kept regardless of completedAt (branch 194 = false).
        JobState running = tracker.start(401L);

        // (b) not running, completedAt == null → kept (branch 194 = true, 195 = false).
        JobState noCompletedAt = tracker.start(402L);
        noCompletedAt.running = false;

        // (c) not running, completedAt long ago → removed (194 = true, 195 = true, 196 = true).
        JobState stale = tracker.start(403L);
        stale.running = false;
        stale.completedAt = Instant.now().minus(2, ChronoUnit.HOURS);

        // (d) not running, completedAt recent → kept (194 = true, 195 = true, 196 = false).
        JobState recent = tracker.start(404L);
        recent.running = false;
        recent.completedAt = Instant.now();

        tracker.cleanup();

        assertThat(tracker.getState(401L)).isPresent();
        assertThat(tracker.getState(402L)).isPresent();
        assertThat(tracker.getState(403L)).isEmpty();
        assertThat(tracker.getState(404L)).isPresent();
    }
}
