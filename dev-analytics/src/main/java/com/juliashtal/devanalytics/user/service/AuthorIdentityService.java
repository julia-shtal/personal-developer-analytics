package com.juliashtal.devanalytics.user.service;

import com.juliashtal.devanalytics.exception.BadRequestException;
import com.juliashtal.devanalytics.exception.ConflictException;
import com.juliashtal.devanalytics.exception.NotFoundException;
import com.juliashtal.devanalytics.exception.UnprocessableEntityException;
import com.juliashtal.devanalytics.github.service.GitHubAccountLookup;
import com.juliashtal.devanalytics.user.model.AuthorIdentityChangedEvent;
import com.juliashtal.devanalytics.user.model.CommitEmailDto;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.model.UserCommitEmail;
import com.juliashtal.devanalytics.user.repository.UserCommitEmailRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Owns the identifiers a user is attributed by: declared commit addresses, the linked GitHub
 * account, and the Jira accountId.
 *
 * <p>Two invariants hold across every operation here.
 *
 * <p><b>Each identifier belongs to exactly one user.</b> Backed by DB constraints — a UNIQUE on
 * {@code user_commit_emails.email} and partial unique indexes on {@code users.github_user_id}
 * and {@code users.jira_account_id} — and checked here first so the caller receives a 409
 * rather than a constraint violation. Two users sharing an identifier would have the same
 * record counted twice in team rollups.
 *
 * <p><b>An event is published only when the effective value changed.</b> The listener reacts by
 * deleting and recomputing every snapshot the user has, which is far too expensive to trigger
 * on a profile save that re-submits an unchanged login. Display-only updates — a GitHub login
 * rename that keeps the same numeric ID — deliberately publish nothing, because attribution is
 * keyed on the ID and no stored metric changes.
 *
 * <p>Addresses and account IDs are never logged at INFO or above; log lines carry user IDs only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorIdentityService {

    private static final int MAX_EMAIL_LENGTH = 255;

    private final UserRepository userRepository;
    private final UserCommitEmailRepository commitEmailRepository;
    private final GitHubAccountLookup gitHubAccountLookup;
    private final ApplicationEventPublisher events;

    /** An added address, plus whether it was newly created (201) or already the caller's (200). */
    public record AddCommitEmailResult(CommitEmailDto email, boolean created) {}

    @Transactional(readOnly = true)
    public List<CommitEmailDto> listCommitEmails(Long userId) {
        return commitEmailRepository.findByUserIdOrderByEmailAsc(userId).stream()
                .map(CommitEmailDto::from)
                .toList();
    }

    @Transactional
    public AddCommitEmailResult addCommitEmail(Long userId, String rawEmail) {
        String email = normalizeEmail(rawEmail);

        Optional<UserCommitEmail> owner = commitEmailRepository.findByEmail(email);
        if (owner.isPresent()) {
            // Idempotent for the caller's own address, so a client retry is not an error.
            if (Objects.equals(owner.get().getUser().getId(), userId)) {
                return new AddCommitEmailResult(CommitEmailDto.from(owner.get()), false);
            }
            log.info("Rejected commit email claim for userId={}: address held by userId={}",
                    userId, owner.get().getUser().getId());
            throw new ConflictException("That address is already linked to another account");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));

        UserCommitEmail entity = new UserCommitEmail();
        entity.setUser(user);
        entity.setEmail(email);
        UserCommitEmail saved = commitEmailRepository.save(entity);

        log.info("Commit email added for userId={}", userId);
        publishChanged(userId);
        return new AddCommitEmailResult(CommitEmailDto.from(saved), true);
    }

    @Transactional
    public void removeCommitEmail(Long userId, Long emailId) {
        UserCommitEmail entity = commitEmailRepository.findByIdAndUserId(emailId, userId)
                .orElseThrow(() -> new NotFoundException("Commit email", emailId));

        commitEmailRepository.delete(entity);
        log.info("Commit email removed for userId={}", userId);
        publishChanged(userId);
    }

    /**
     * Links the GitHub account named by {@code login}, resolving it to a numeric ID first.
     * A blank login unlinks the account. The login and the ID are always written together.
     */
    @Transactional
    public void setGithubIdentity(Long userId, String login) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));

        if (login == null || login.isBlank()) {
            boolean changed = user.getGithubUserId() != null;
            user.setGithubLogin(null);
            user.setGithubUserId(null);
            userRepository.save(user);
            if (changed) {
                log.info("GitHub identity unlinked for userId={}", userId);
                publishChanged(userId);
            }
            return;
        }

        GitHubAccountLookup.GitHubAccount account = gitHubAccountLookup.findByLogin(userId, login)
                .orElseThrow(() -> new UnprocessableEntityException("GitHub login not found"));

        requireGithubAccountUnclaimed(userId, account.id());

        boolean changed = !Objects.equals(user.getGithubUserId(), account.id());
        // Store the login exactly as GitHub spells it, not as the user typed it, so the
        // display value cannot drift from the account it names.
        user.setGithubUserId(account.id());
        user.setGithubLogin(account.login());
        userRepository.save(user);

        if (changed) {
            log.info("GitHub identity linked for userId={}", userId);
            publishChanged(userId);
        }
    }

    /**
     * Links the GitHub account owning a collection token, without the user typing anything.
     * Never overrides an existing link and never takes one held by someone else — both cases
     * are no-ops, because a collection run is the wrong place to resolve an ownership dispute.
     */
    @Transactional
    public void claimGithubIdentityIfAbsent(Long userId, long githubUserId, String login) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        if (Objects.equals(user.getGithubUserId(), githubUserId)) {
            // Same account, different spelling: the user renamed themselves on GitHub. Refresh
            // the display value only. Attribution is keyed on the ID, so no stored metric is
            // affected and no recompute is warranted.
            if (login != null && !login.equals(user.getGithubLogin())) {
                user.setGithubLogin(login);
                userRepository.save(user);
                log.info("GitHub login refreshed after rename for userId={}", userId);
            }
            return;
        }

        if (user.getGithubUserId() != null) return;

        if (githubAccountHolder(githubUserId, userId).isPresent()) {
            log.warn("Not claiming GitHub identity for userId={}: already held by another user", userId);
            return;
        }

        user.setGithubUserId(githubUserId);
        if (login != null && !login.isBlank()) user.setGithubLogin(login);
        userRepository.save(user);
        log.info("GitHub identity claimed from collection token for userId={}", userId);
        publishChanged(userId);
    }

    @Transactional
    public void setJiraAccountId(Long userId, String rawAccountId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));

        String accountId = (rawAccountId == null || rawAccountId.isBlank()) ? null : rawAccountId.trim();
        if (Objects.equals(user.getJiraAccountId(), accountId)) return;

        if (accountId != null) requireJiraAccountUnclaimed(userId, accountId);

        user.setJiraAccountId(accountId);
        userRepository.save(user);
        log.info("Jira account {} for userId={}", accountId == null ? "unlinked" : "linked", userId);
        publishChanged(userId);
    }

    /** Same no-op contract as {@link #claimGithubIdentityIfAbsent}, for the Jira token owner. */
    @Transactional
    public void claimJiraAccountIdIfAbsent(Long userId, String accountId) {
        if (userId == null || accountId == null || accountId.isBlank()) return;

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getJiraAccountId() != null) return;

        String trimmed = accountId.trim();
        if (jiraAccountHolder(trimmed, userId).isPresent()) {
            log.warn("Not claiming Jira account for userId={}: already held by another user", userId);
            return;
        }

        user.setJiraAccountId(trimmed);
        userRepository.save(user);
        log.info("Jira account claimed from collection token for userId={}", userId);
        publishChanged(userId);
    }

    private void requireGithubAccountUnclaimed(Long userId, long githubUserId) {
        githubAccountHolder(githubUserId, userId).ifPresent(other -> {
            log.info("Rejected GitHub identity claim for userId={}: account held by userId={}",
                    userId, other.getId());
            throw new ConflictException("That GitHub account is already linked to another account");
        });
    }

    private void requireJiraAccountUnclaimed(Long userId, String accountId) {
        jiraAccountHolder(accountId, userId).ifPresent(other -> {
            log.info("Rejected Jira account claim for userId={}: held by userId={}",
                    userId, other.getId());
            throw new ConflictException("That Jira account is already linked to another account");
        });
    }

    private Optional<User> githubAccountHolder(long githubUserId, Long excludingUserId) {
        return userRepository.findByGithubUserId(githubUserId)
                .filter(u -> !Objects.equals(u.getId(), excludingUserId));
    }

    private Optional<User> jiraAccountHolder(String accountId, Long excludingUserId) {
        return userRepository.findByJiraAccountId(accountId)
                .filter(u -> !Objects.equals(u.getId(), excludingUserId));
    }

    /**
     * Trims and lower-cases once, here, so every stored address satisfies the
     * {@code CHECK (email = lower(btrim(email)))} constraint and is directly comparable
     * against the {@code lower(author_email)} index the commit queries use.
     */
    private String normalizeEmail(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Email must not be blank");
        }
        String email = raw.trim().toLowerCase(Locale.ROOT);
        if (email.length() > MAX_EMAIL_LENGTH) {
            throw new BadRequestException("Email must be at most " + MAX_EMAIL_LENGTH + " characters");
        }
        if (email.chars().anyMatch(Character::isWhitespace)) {
            throw new BadRequestException("Email must not contain whitespace");
        }
        if (!email.contains("@")) {
            throw new BadRequestException("Email must contain an @ sign");
        }
        return email;
    }

    private void publishChanged(Long userId) {
        events.publishEvent(new AuthorIdentityChangedEvent(userId));
    }
}
