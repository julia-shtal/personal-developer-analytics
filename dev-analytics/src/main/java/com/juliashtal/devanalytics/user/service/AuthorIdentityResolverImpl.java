package com.juliashtal.devanalytics.user.service;

import com.juliashtal.devanalytics.user.model.AuthorIdentity;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserCommitEmailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads the declared identity out of {@code user_commit_emails} and the users row.
 */
@Service
@RequiredArgsConstructor
public class AuthorIdentityResolverImpl implements AuthorIdentityResolver {

    private final UserCommitEmailRepository commitEmailRepository;

    @Override
    @Transactional(readOnly = true)
    public AuthorIdentity resolve(User user) {
        if (user == null || user.getId() == null) return AuthorIdentity.empty();

        // Deliberately not seeded with user.getEmail(): the account address is copied into
        // user_commit_emails once, and folding it back would resurrect a removed address.
        Set<String> emails = commitEmailRepository.findEmailsByUserId(user.getId()).stream()
                .filter(e -> e != null && !e.isBlank())
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());

        return new AuthorIdentity(emails, user.getGithubUserId(), user.getJiraAccountId());
    }
}
