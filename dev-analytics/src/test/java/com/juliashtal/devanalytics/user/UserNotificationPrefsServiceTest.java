package com.juliashtal.devanalytics.user;

import com.juliashtal.devanalytics.user.model.NotificationPrefsDto;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.model.UserNotificationPrefsEntity;
import com.juliashtal.devanalytics.user.repository.UserNotificationPrefsRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.user.service.UserNotificationPrefsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserNotificationPrefsServiceTest {

    @Mock UserNotificationPrefsRepository prefsRepository;
    @Mock UserRepository userRepository;
    @InjectMocks UserNotificationPrefsService service;

    @Test
    void getOrCreate_existingPrefs_returnsExisting() {
        User user = new User();
        user.setId(1L);
        UserNotificationPrefsEntity existing = new UserNotificationPrefsEntity(user);
        when(prefsRepository.findById(1L)).thenReturn(Optional.of(existing));

        UserNotificationPrefsEntity result = service.getOrCreate(1L);

        assertThat(result).isSameAs(existing);
        verify(prefsRepository, never()).save(any());
    }

    @Test
    void getOrCreate_noPrefs_createsWithDefaults() {
        User user = new User();
        user.setId(1L);
        when(prefsRepository.findById(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(prefsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserNotificationPrefsEntity result = service.getOrCreate(1L);

        assertThat(result.isAiBrief()).isTrue();
        assertThat(result.isSyncFailures()).isTrue();
        assertThat(result.isAfterHours()).isTrue();
        assertThat(result.isNewTeamMember()).isFalse();
        verify(prefsRepository).save(any());
    }

    @Test
    void getOrCreate_idempotent_doesNotSaveTwice() {
        User user = new User();
        user.setId(1L);
        UserNotificationPrefsEntity existing = new UserNotificationPrefsEntity(user);
        when(prefsRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.getOrCreate(1L);
        service.getOrCreate(1L);

        verify(prefsRepository, never()).save(any());
    }

    @Test
    void update_persistsAllFields() {
        User user = new User();
        user.setId(1L);
        UserNotificationPrefsEntity existing = new UserNotificationPrefsEntity(user);
        when(prefsRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(prefsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPrefsDto dto = new NotificationPrefsDto(false, false, true, true);
        NotificationPrefsDto result = service.update(1L, dto);

        assertThat(result.aiBrief()).isFalse();
        assertThat(result.syncFailures()).isFalse();
        assertThat(result.afterHours()).isTrue();
        assertThat(result.newTeamMember()).isTrue();
    }
}