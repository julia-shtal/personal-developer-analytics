package com.juliashtal.devanalytics.user.repository;

import com.juliashtal.devanalytics.user.model.UserNotificationPrefsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for UserNotificationPrefsEntity (user_notification_prefs).
 */
public interface UserNotificationPrefsRepository extends JpaRepository<UserNotificationPrefsEntity, Long> {
}