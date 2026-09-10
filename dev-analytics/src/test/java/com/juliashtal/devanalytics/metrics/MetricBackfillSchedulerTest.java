package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.metrics.model.BackfillResult;
import com.juliashtal.devanalytics.metrics.service.MetricBackfillScheduler;
import com.juliashtal.devanalytics.metrics.service.MetricBackfillService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricBackfillSchedulerTest {

    @Mock UserRepository userRepository;
    @Mock MetricBackfillService backfillService;

    private User userWithId(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    @Test
    void backfillAll_everyUser_isBackfilled() {
        when(userRepository.findAll()).thenReturn(List.of(userWithId(1L), userWithId(2L)));
        when(backfillService.backfillUser(anyLong()))
                .thenReturn(new BackfillResult(0, 0, LocalDate.now(), LocalDate.now()));

        new MetricBackfillScheduler(userRepository, backfillService).backfillAll();

        verify(backfillService).backfillUser(1L);
        verify(backfillService).backfillUser(2L);
    }

    @Test
    void backfillAll_oneUserFails_othersStillProcessed() {
        when(userRepository.findAll())
                .thenReturn(List.of(userWithId(1L), userWithId(2L), userWithId(3L)));
        when(backfillService.backfillUser(2L)).thenThrow(new IllegalStateException("boom"));
        when(backfillService.backfillUser(1L))
                .thenReturn(new BackfillResult(0, 0, LocalDate.now(), LocalDate.now()));
        when(backfillService.backfillUser(3L))
                .thenReturn(new BackfillResult(0, 0, LocalDate.now(), LocalDate.now()));

        new MetricBackfillScheduler(userRepository, backfillService).backfillAll();

        verify(backfillService).backfillUser(1L);
        verify(backfillService).backfillUser(3L);
    }
}
