package com.juliashtal.devanalytics.datasource.controller;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.SyncJobEntity;
import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;
import com.juliashtal.devanalytics.datasource.model.dto.DataSourceResponseDto;
import com.juliashtal.devanalytics.datasource.model.dto.SyncJobSummaryDto;
import com.juliashtal.devanalytics.datasource.model.dto.SyncStatusResponse;
import com.juliashtal.devanalytics.datasource.model.dto.UpdateDataSourceRequest;
import com.juliashtal.devanalytics.datasource.service.AsyncDataSourceCollectService;
import com.juliashtal.devanalytics.datasource.service.DataSourceService;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for data sources.
 * Mounted at /api/datasources — CRUD plus async collection orchestration.
 */
@RestController
@RequestMapping("/api/datasources")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class DataSourceController {

    private final DataSourceService dataSourceService;
    private final AsyncDataSourceCollectService asyncCollectService;
    private final SyncJobTracker syncJobTracker;

    @Operation(summary = "Create a new data source, reusing an existing canonical one if it already exists")
    @PostMapping
    public ResponseEntity<DataSourceResponseDto> create(@RequestBody @Valid CreateDataSourceRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        DataSourceResponseDto created = dataSourceService.create(userId, request);
        return ResponseEntity
                .created(URI.create("/api/datasources/" + created.id()))
                .body(created);
    }

    @Operation(summary = "List the current user's data sources")
    @GetMapping
    public List<DataSourceResponseDto> list() {
        Long userId = SecurityUtils.getCurrentUserId();
        return dataSourceService.listForUser(userId);
    }

    @Operation(summary = "Get a single data source by ID")
    @GetMapping("/{id}")
    public DataSourceConfig get(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return dataSourceService.getForUser(userId, id);
    }

    @Operation(summary = "Update a data source's configuration")
    @PutMapping("/{id}")
    public DataSourceConfig update(@PathVariable Long id,
                                   @RequestBody UpdateDataSourceRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return dataSourceService.update(userId, id, request);
    }

    @Operation(summary = "Delete a data source")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        dataSourceService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Triggers background collection. Authorization is checked synchronously
     * before returning 202 so the client gets an immediate error on bad access.
     */
    @Operation(summary = "Trigger background collection for a data source (returns 202 Accepted)")
    @PostMapping("/{id}/collect")
    public ResponseEntity<Void> collect(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        dataSourceService.getForUser(userId, id);
        asyncCollectService.collectAsync(userId, id);
        return ResponseEntity.accepted().build();
    }

    /**
     * Live status of the most recent collection job for one data source.
     * Falls back to the persisted DB record when the in-memory entry is gone
     * (e.g. after a restart, or once the 1-hour in-memory cleanup ran).
     */
    @Operation(summary = "Get the live status of the most recent collection job for a data source")
    @GetMapping("/{id}/collect/status")
    public ResponseEntity<SyncStatusResponse> collectStatus(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        dataSourceService.getForUser(userId, id);
        return syncJobTracker.getState(id)
                .map(state -> ResponseEntity.ok(buildResponse(state)))
                .or(() -> syncJobTracker.findLatestPersisted(id)
                        .map(entity -> ResponseEntity.ok(buildResponseFromEntity(entity))))
                .orElse(ResponseEntity.notFound().<SyncStatusResponse>build());
    }

    /**
     * Returns all active (or recently completed) collection jobs for the current
     * user's data sources. Used by the frontend on page mount to restore progress
     * state after navigation.
     *
     * Response: {@code { "datasourceId": SyncStatusResponse, … }}
     */
    @Operation(summary = "Get all active or recently completed collection jobs for the current user's data sources")
    @GetMapping("/collect/status/active")
    public Map<Long, SyncStatusResponse> activeCollectStatuses() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<DataSourceResponseDto> sources = dataSourceService.listForUser(userId);

        Map<Long, SyncStatusResponse> result = new LinkedHashMap<>();
        for (DataSourceResponseDto src : sources) {
            syncJobTracker.getState(src.id()).ifPresent(state -> {
                // Include if still running, or completed within the last 5 minutes
                boolean recentlyDone = !state.running
                        && state.completedAt != null
                        && Duration.between(state.completedAt, Instant.now()).toMinutes() < 5;
                if (state.running || recentlyDone) {
                    result.put(src.id(), buildResponse(state));
                }
            });
        }
        return result;
    }

    /**
     * Returns the most recent sync jobs for a data source (default limit: 5, max: 20).
     * Used by the frontend to derive the health badge and show a compact history timeline.
     */
    @Operation(summary = "Get recent sync job history for a data source")
    @GetMapping("/{id}/sync-history")
    public List<SyncJobSummaryDto> getSyncHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "5") int limit) {
        Long userId = SecurityUtils.getCurrentUserId();
        return dataSourceService.getSyncHistory(id, limit, userId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds a terminal response from a persisted DB record (post-restart fallback). */
    private SyncStatusResponse buildResponseFromEntity(SyncJobEntity entity) {
        long elapsed = entity.getCompletedAt() != null
                ? Duration.between(entity.getStartedAt(), entity.getCompletedAt()).getSeconds()
                : Duration.between(entity.getStartedAt(), Instant.now()).getSeconds();
        String result = switch (entity.getStatus()) {
            case INTERRUPTED -> "Job was interrupted by a server restart";
            case FAILED -> null;
            default -> entity.getResult();
        };
        String error = entity.getStatus() == com.juliashtal.devanalytics.datasource.model.SyncJobStatus.FAILED
                ? entity.getError() : null;
        return new SyncStatusResponse(
                false, 0, 1,
                entity.getPhase() != null ? entity.getPhase() : entity.getStatus().name().toLowerCase(),
                0, -1,
                entity.getTotalProcessed() != null ? entity.getTotalProcessed() : 0,
                elapsed, null, null, List.of(), result, error
        );
    }

    private SyncStatusResponse buildResponse(SyncJobTracker.JobState state) {
        long elapsed = Duration.between(state.startedAt, Instant.now()).getSeconds();
        Long phaseEta = syncJobTracker.phaseEtaSeconds(state);
        Long overallEta = syncJobTracker.overallEtaSeconds(state);

        List<SyncStatusResponse.CompletedPhase> history = state.completedPhases.stream()
                .map(p -> new SyncStatusResponse.CompletedPhase(p.name(), p.itemsSaved(), p.durationSeconds()))
                .toList();

        return new SyncStatusResponse(
                state.running,
                state.phaseNumber,
                state.totalPhases,
                state.phase,
                state.phaseProcessed.get(),
                state.phaseTotal,
                state.totalProcessed.get(),
                elapsed,
                phaseEta,
                overallEta,
                history,
                state.result,
                state.error
        );
    }
}
