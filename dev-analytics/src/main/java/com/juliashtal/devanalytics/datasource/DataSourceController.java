package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;
import com.juliashtal.devanalytics.datasource.model.dto.DataSourceResponseDto;
import com.juliashtal.devanalytics.datasource.model.dto.SyncStatusResponse;
import com.juliashtal.devanalytics.datasource.model.dto.UpdateDataSourceRequest;
import com.juliashtal.devanalytics.datasource.service.AsyncDataSourceCollectService;
import com.juliashtal.devanalytics.datasource.service.DataSourceService;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.security.SecurityUtils;
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

@RestController
@RequestMapping("/api/datasources")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class DataSourceController {

    private final DataSourceService dataSourceService;
    private final AsyncDataSourceCollectService asyncCollectService;
    private final SyncJobTracker syncJobTracker;

    @PostMapping
    public ResponseEntity<DataSourceConfig> create(@RequestBody @Valid CreateDataSourceRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        DataSourceConfig created = dataSourceService.create(userId, request);
        if (created == null) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity
                .created(URI.create("/api/datasources/" + created.getId()))
                .body(created);
    }

    @GetMapping
    public List<DataSourceResponseDto> list() {
        Long userId = SecurityUtils.getCurrentUserId();
        return dataSourceService.listForUser(userId);
    }

    @GetMapping("/{id}")
    public DataSourceConfig get(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return dataSourceService.getForUser(userId, id);
    }

    @PutMapping("/{id}")
    public DataSourceConfig update(@PathVariable Long id,
                                   @RequestBody UpdateDataSourceRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return dataSourceService.update(userId, id, request);
    }

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
    @PostMapping("/{id}/collect")
    public ResponseEntity<Void> collect(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        dataSourceService.getForUser(userId, id);
        asyncCollectService.collectAsync(userId, id);
        return ResponseEntity.accepted().build();
    }

    /**
     * Live status of the most recent collection job for one data source.
     * Returns 404 when no job has run since the last server restart.
     */
    @GetMapping("/{id}/collect/status")
    public ResponseEntity<SyncStatusResponse> collectStatus(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        dataSourceService.getForUser(userId, id);
        return syncJobTracker.getState(id)
                .map(state -> ResponseEntity.ok(buildResponse(state)))
                .orElse(ResponseEntity.notFound().<SyncStatusResponse>build());
    }

    /**
     * Returns all active (or recently completed) collection jobs for the current
     * user's data sources. Used by the frontend on page mount to restore progress
     * state after navigation.
     *
     * Response: {@code { "datasourceId": SyncStatusResponse, … }}
     */
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

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

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
