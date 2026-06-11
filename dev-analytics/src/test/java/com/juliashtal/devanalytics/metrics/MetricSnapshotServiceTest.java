package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricSnapshotServiceTest {

    @Mock MetricSnapshotRepository repository;
    @InjectMocks MetricSnapshotService service;

    @Test
    void findMaxPersonalDate_delegatesToRepository() {
        LocalDate date = LocalDate.of(2026, 6, 1);
        when(repository.findMaxPersonalDate(1L)).thenReturn(Optional.of(date));

        Optional<LocalDate> result = service.findMaxPersonalDate(1L);

        assertThat(result).contains(date);
    }

    @Test
    void findMaxPersonalDate_noSnapshots_returnsEmpty() {
        when(repository.findMaxPersonalDate(1L)).thenReturn(Optional.empty());

        assertThat(service.findMaxPersonalDate(1L)).isEmpty();
    }
}
