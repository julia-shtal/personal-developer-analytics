package com.juliashtal.devanalytics.user.repository;

import com.juliashtal.devanalytics.user.model.UserNotificationPrefsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationPrefsRepository extends JpaRepository<UserNotificationPrefsEntity, Long> {
}