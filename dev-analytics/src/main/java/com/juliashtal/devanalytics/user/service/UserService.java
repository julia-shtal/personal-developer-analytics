package com.juliashtal.devanalytics.user.service;

import com.juliashtal.devanalytics.exception.BadRequestException;
import com.juliashtal.devanalytics.exception.ConflictException;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.request.UpdateProfileRequest;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public User getById(Long userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
    }

    /** Lazy reference, sufficient for FK comparisons/inserts without loading the row. */
    public User getReferenceById(Long userId) {
        return repository.getReferenceById(userId);
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

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            log.warn("Failed password change attempt for userId={}: incorrect current password", userId);
            throw new BadRequestException("Current password is incorrect");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new BadRequestException("New password must be at least 8 characters");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        repository.save(user);
        log.info("Password changed for userId={}", userId);
    }

    public User updateRole(Long userId, Role role) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        user.setRole(role);
        User saved = repository.save(user);
        log.info("Role updated for userId={} to {}", userId, role);
        return saved;
    }

    public List<User> findAll() {
        return repository.findAll();
    }

    public List<User> search(String q) {
        if (q == null || q.isBlank()) return repository.findAll();
        return repository.searchByEmailOrUsername(q.trim());
    }

    public void delete(Long userId) {
        if (!repository.existsById(userId)) {
            throw new NoSuchElementException("User not found");
        }
        repository.deleteById(userId);
        log.info("User deleted: id={}", userId);
    }

    @Transactional
    public void touchLastActive(Long userId) {
        repository.touchLastActive(userId);
    }

    @Transactional
    public void deleteSelf(Long userId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        if (user.getRole() == Role.ADMIN) {
            long adminCount = repository.countByRole(Role.ADMIN);
            if (adminCount <= 1) {
                throw new ConflictException("Cannot delete the last admin account — promote another user first.");
            }
        }
        repository.deleteById(userId);
        log.info("User self-deleted: id={}", userId);
    }
}