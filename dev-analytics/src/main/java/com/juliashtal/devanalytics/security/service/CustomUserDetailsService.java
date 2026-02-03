package com.juliashtal.devanalytics.security.service;

import com.juliashtal.devanalytics.security.model.CustomUserDetails;
import com.juliashtal.devanalytics.user.User;
import com.juliashtal.devanalytics.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

// TODO Загружает пользователя по username (можно расширить до email)
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(usernameOrEmail)
                .orElseGet(() -> userRepository.findByEmail(usernameOrEmail)
                        .orElseThrow(() -> new NoSuchElementException("User not found")));
        return new CustomUserDetails(user);
    }
}

