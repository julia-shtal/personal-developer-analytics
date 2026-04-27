package com.juliashtal.devanalytics.user.service;

import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.request.UpdateProfileRequest;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository repository;

    public User getById(Long userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
    }

    @Transactional
    public User updateProfile(Long userId, UpdateProfileRequest req) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        if (req.getUsername() != null) user.setUsername(req.getUsername());
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            user.setEmail(req.getEmail());
        }
        if (req.getTimezone() != null) user.setTimezone(req.getTimezone());
        if (req.getGithubLogin() != null) user.setGithubLogin(req.getGithubLogin());
        return repository.save(user);
    }

    public User updateRole(Long userId, Role role) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        user.setRole(role);
        return repository.save(user);
    }

    public List<User> findAll() {
        return repository.findAll();
    }

    public void delete(Long userId) {
        if (!repository.existsById(userId)) {
            throw new NoSuchElementException("User not found");
        }
        repository.deleteById(userId);
    }
}

