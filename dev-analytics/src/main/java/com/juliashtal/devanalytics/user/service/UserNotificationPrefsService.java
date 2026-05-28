package com.juliashtal.devanalytics.user.service;

import com.juliashtal.devanalytics.user.model.NotificationPrefsDto;
import com.juliashtal.devanalytics.user.model.UserNotificationPrefsEntity;
import com.juliashtal.devanalytics.user.repository.UserNotificationPrefsRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class UserNotificationPrefsService {

    private final UserNotificationPrefsRepository prefsRepository;
    private final UserRepository userRepository;

    @Transactional
    public UserNotificationPrefsEntity getOrCreate(Long userId) {
        return prefsRepository.findById(userId).orElseGet(() -> {
            var user = userRepository.findById(userId)
                    .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
            return prefsRepository.save(new UserNotificationPrefsEntity(user));
        });
    }

    @Transactional
    public NotificationPrefsDto update(Long userId, NotificationPrefsDto dto) {
        UserNotificationPrefsEntity prefs = getOrCreate(userId);
        prefs.setAiBrief(dto.aiBrief());
        prefs.setSyncFailures(dto.syncFailures());
        prefs.setAfterHours(dto.afterHours());
        prefs.setNewTeamMember(dto.newTeamMember());
        return NotificationPrefsDto.from(prefsRepository.save(prefs));
    }
}