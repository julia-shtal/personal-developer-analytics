package com.juliashtal.devanalytics.user.service;

import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository repository;

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

