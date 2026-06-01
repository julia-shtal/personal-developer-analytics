package com.juliashtal.devanalytics.datasource.service;

import com.juliashtal.devanalytics.datasource.model.SyncJobStatus;
import com.juliashtal.devanalytics.datasource.repository.SyncJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * At startup, any sync job still marked RUNNING could not have finished — the process was killed.
 * Mark them INTERRUPTED so the UI never shows a perpetual "syncing..." spinner.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SyncJobStartupMarker implements ApplicationRunner {

    private final SyncJobRepository syncJobRepository;

    @Override
    public void run(ApplicationArguments args) {
        int count = syncJobRepository.markInterrupted(SyncJobStatus.RUNNING, SyncJobStatus.INTERRUPTED, Instant.now());
        if (count > 0) {
            log.info("Marked {} sync job(s) as INTERRUPTED after restart", count);
        }
    }
}
