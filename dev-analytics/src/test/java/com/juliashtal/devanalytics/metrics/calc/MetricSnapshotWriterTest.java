package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.metrics.MetricSnapshotRepository;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.argThat;

@ExtendWith(MockitoExtension.class)
class MetricSnapshotWriterTest {

    @Mock MetricSnapshotRepository repository;

    @InjectMocks MetricSnapshotWriter writer;

    private static final LocalDate DATE = LocalDate.of(2024, 1, 15);

    @Test
    void save_newSnapshot_savesNewRow() {
        when(repository.findExisting(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        User user = new User();
        user.setId(1L);

        writer.save(user, null, DATE, MetricType.DAILY_COMMITS_COUNT, 7.0, null, null, null);

        verify(repository).save(argThat(s ->
                s.getValue() == 7.0 && s.getMetricType() == MetricType.DAILY_COMMITS_COUNT && s.getUser() == user
        ));
    }

    @Test
    void save_existingSnapshot_updatesValueWithoutDuplicate() {
        MetricSnapshot existing = new MetricSnapshot();
        existing.setId(99L);
        existing.setValue(3.0);
        when(repository.findExisting(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(existing));

        User user = new User();
        user.setId(1L);

        writer.save(user, null, DATE, MetricType.DAILY_COMMITS_COUNT, 7.0, null, null, null);

        verify(repository, times(1)).save(argThat(s ->
                s.getId() == 99L && s.getValue() == 7.0
        ));
    }
}
