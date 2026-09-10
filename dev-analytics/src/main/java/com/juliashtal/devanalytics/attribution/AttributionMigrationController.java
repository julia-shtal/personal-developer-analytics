package com.juliashtal.devanalytics.attribution;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin control for the one-off author-attribution migration.
 *
 * <p>Deliberately manual rather than run on startup: it makes a GitHub API call per page of every
 * repository's history, and an operator should choose when to spend that.
 */
@RestController
@RequestMapping("/api/admin/attribution/migrate")
@RequiredArgsConstructor
public class AttributionMigrationController {

    private final AttributionMigrationService migrationService;

    @Operation(summary = "Start the author-attribution migration",
            description = "Fills the numeric identity columns on records collected before author "
                    + "attribution existed, then recomputes metrics once every repository is done. "
                    + "Returns immediately; poll the GET endpoint for progress.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Migration started"),
            @ApiResponse(responseCode = "409", description = "Already running, or the demo profile is active")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Void> start() {
        migrationService.start();
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Report author-attribution migration progress")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<AttributionMigrationStatus> status() {
        return ResponseEntity.ok(migrationService.status());
    }
}
