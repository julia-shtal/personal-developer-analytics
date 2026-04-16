package com.juliashtal.devanalytics.security;

import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CheckHelper {

    private final UserRepository userRepository;

    public User currentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        return userRepository.getReferenceById(userId);
    }
}
