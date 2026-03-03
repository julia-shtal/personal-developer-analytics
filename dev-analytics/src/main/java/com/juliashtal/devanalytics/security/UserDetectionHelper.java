package com.juliashtal.devanalytics.security;

import com.juliashtal.devanalytics.user.User;
import com.juliashtal.devanalytics.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetectionHelper {

    private final UserRepository userRepository;

    public User currentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        return userRepository.getReferenceById(userId);
    }
}
