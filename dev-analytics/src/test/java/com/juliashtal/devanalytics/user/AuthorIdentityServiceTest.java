package com.juliashtal.devanalytics.user;

import com.juliashtal.devanalytics.exception.BadRequestException;
import com.juliashtal.devanalytics.exception.ConflictException;
import com.juliashtal.devanalytics.exception.UnprocessableEntityException;
import com.juliashtal.devanalytics.github.service.GitHubAccountLookup;
import com.juliashtal.devanalytics.user.model.AuthorIdentityChangedEvent;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.model.UserCommitEmail;
import com.juliashtal.devanalytics.user.repository.UserCommitEmailRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.user.service.AuthorIdentityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Acceptance tests for identity ownership, GitHub login resolution, and event discipline.
 *
 * <p>Three rules are pinned: each identifier belongs to exactly one user, so a second claim is a
 * 409; a login is always resolved to a numeric account first, so an unresolvable one is a 422 and
 * a rename is not a new identity; and an event fires only on an effective change, because the
 * listener recomputes every snapshot the user has.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthorIdentityServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserCommitEmailRepository commitEmailRepository;
    @Mock GitHubAccountLookup gitHubAccountLookup;
    @Mock ApplicationEventPublisher events;

    @InjectMocks AuthorIdentityService service;

    private User user;
    private User otherUser;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("alice@example.com");

        otherUser = new User();
        otherUser.setId(2L);
    }

    // ── Commit emails ────────────────────────────────────────────────────────

    @Test
    void addCommitEmail_unclaimedAddress_storesItNormalisedAndPublishesOneEvent() {
        when(commitEmailRepository.findByEmail("dev@example.com")).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commitEmailRepository.save(any(UserCommitEmail.class))).thenAnswer(inv -> inv.getArgument(0));

        // Mixed case and surrounding space: the CHECK constraint on user_commit_emails only
        // accepts lower(btrim(...)), and the query compares against lower(author_email).
        AuthorIdentityService.AddCommitEmailResult result =
                service.addCommitEmail(1L, "  Dev@Example.com  ");

        assertThat(result.created()).isTrue();
        assertThat(result.email().email()).isEqualTo("dev@example.com");
        verify(events, times(1)).publishEvent(new AuthorIdentityChangedEvent(1L));
    }

    @Test
    void addCommitEmail_callerAlreadyOwnsIt_returnsExistingAndPublishesNothing() {
        UserCommitEmail owned = new UserCommitEmail();
        owned.setId(9L);
        owned.setUser(user);
        owned.setEmail("dev@example.com");
        when(commitEmailRepository.findByEmail("dev@example.com")).thenReturn(Optional.of(owned));

        AuthorIdentityService.AddCommitEmailResult result = service.addCommitEmail(1L, "dev@example.com");

        // 200, not 201: a client retry is not an error, and nothing changed so nothing is recomputed.
        assertThat(result.created()).isFalse();
        verify(commitEmailRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void addCommitEmail_addressHeldByAnotherUser_throwsConflict() {
        UserCommitEmail owned = new UserCommitEmail();
        owned.setUser(otherUser);
        owned.setEmail("dev@example.com");
        when(commitEmailRepository.findByEmail("dev@example.com")).thenReturn(Optional.of(owned));

        assertThatThrownBy(() -> service.addCommitEmail(1L, "dev@example.com"))
                .isInstanceOf(ConflictException.class);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void addCommitEmail_addressWithoutAtSign_throwsBadRequest() {
        assertThatThrownBy(() -> service.addCommitEmail(1L, "not-an-address"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void addCommitEmail_addressContainingWhitespace_throwsBadRequest() {
        assertThatThrownBy(() -> service.addCommitEmail(1L, "two words@example.com"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void removeCommitEmail_ownedAddress_deletesItAndPublishesOneEvent() {
        UserCommitEmail owned = new UserCommitEmail();
        owned.setId(9L);
        owned.setUser(user);
        owned.setEmail("dev@example.com");
        when(commitEmailRepository.findByIdAndUserId(9L, 1L)).thenReturn(Optional.of(owned));

        service.removeCommitEmail(1L, 9L);

        verify(commitEmailRepository).delete(owned);
        verify(events, times(1)).publishEvent(new AuthorIdentityChangedEvent(1L));
    }

    // ── GitHub identity via profile save ─────────────────────────────────────

    @Test
    void setGithubIdentity_resolvableLogin_storesIdAndCanonicalLoginAndPublishesOneEvent() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(gitHubAccountLookup.findByLogin(1L, "OctoCat"))
                .thenReturn(Optional.of(new GitHubAccountLookup.GitHubAccount(101L, "octocat")));
        when(userRepository.findByGithubUserId(101L)).thenReturn(Optional.empty());

        service.setGithubIdentity(1L, "OctoCat");

        assertThat(user.getGithubUserId()).isEqualTo(101L);
        // GitHub's spelling wins over the user's, so the display value cannot drift from the
        // account it names.
        assertThat(user.getGithubLogin()).isEqualTo("octocat");
        verify(events, times(1)).publishEvent(new AuthorIdentityChangedEvent(1L));
    }

    @Test
    void setGithubIdentity_unknownLogin_throwsUnprocessableEntity() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(gitHubAccountLookup.findByLogin(1L, "ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setGithubIdentity(1L, "ghost"))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("not found");
        verify(events, never()).publishEvent(any());
    }

    @Test
    void setGithubIdentity_accountHeldByAnotherUser_throwsConflict() {
        otherUser.setGithubUserId(101L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(gitHubAccountLookup.findByLogin(1L, "octocat"))
                .thenReturn(Optional.of(new GitHubAccountLookup.GitHubAccount(101L, "octocat")));
        when(userRepository.findByGithubUserId(101L)).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> service.setGithubIdentity(1L, "octocat"))
                .isInstanceOf(ConflictException.class);
        assertThat(user.getGithubUserId()).isNull();
        verify(events, never()).publishEvent(any());
    }

    @Test
    void setGithubIdentity_sameAccountResubmitted_publishesNothing() {
        user.setGithubUserId(101L);
        user.setGithubLogin("octocat");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(gitHubAccountLookup.findByLogin(1L, "octocat"))
                .thenReturn(Optional.of(new GitHubAccountLookup.GitHubAccount(101L, "octocat")));
        when(userRepository.findByGithubUserId(101L)).thenReturn(Optional.of(user));

        service.setGithubIdentity(1L, "octocat");

        // Attribution is unchanged, so recomputing every snapshot would be pure waste.
        verify(events, never()).publishEvent(any());
    }

    @Test
    void setGithubIdentity_blankLoginOnLinkedAccount_unlinksAndPublishesOneEvent() {
        user.setGithubUserId(101L);
        user.setGithubLogin("octocat");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.setGithubIdentity(1L, "  ");

        assertThat(user.getGithubUserId()).isNull();
        assertThat(user.getGithubLogin()).isNull();
        verify(events, times(1)).publishEvent(new AuthorIdentityChangedEvent(1L));
    }

    // ── GitHub identity claimed from a collection token ──────────────────────

    @Test
    void claimGithubIdentityIfAbsent_noExistingIdentity_claimsItAndPublishesOneEvent() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByGithubUserId(101L)).thenReturn(Optional.empty());

        service.claimGithubIdentityIfAbsent(1L, 101L, "octocat");

        assertThat(user.getGithubUserId()).isEqualTo(101L);
        verify(events, times(1)).publishEvent(new AuthorIdentityChangedEvent(1L));
    }

    @Test
    void claimGithubIdentityIfAbsent_sameAccountRenamed_updatesLoginWithoutPublishing() {
        user.setGithubUserId(101L);
        user.setGithubLogin("old-name");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.claimGithubIdentityIfAbsent(1L, 101L, "new-name");

        assertThat(user.getGithubLogin()).isEqualTo("new-name");
        // The id did not move, so no stored metric changes and no recompute is warranted.
        verify(events, never()).publishEvent(any());
    }

    @Test
    void claimGithubIdentityIfAbsent_accountHeldByAnotherUser_isANoOp() {
        otherUser.setGithubUserId(101L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByGithubUserId(101L)).thenReturn(Optional.of(otherUser));

        service.claimGithubIdentityIfAbsent(1L, 101L, "octocat");

        // A collection run is the wrong place to resolve an ownership dispute.
        assertThat(user.getGithubUserId()).isNull();
        verify(userRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void claimGithubIdentityIfAbsent_userAlreadyLinkedToADifferentAccount_isANoOp() {
        user.setGithubUserId(555L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.claimGithubIdentityIfAbsent(1L, 101L, "octocat");

        assertThat(user.getGithubUserId()).isEqualTo(555L);
        verify(events, never()).publishEvent(any());
    }

    // ── Jira identity ────────────────────────────────────────────────────────

    @Test
    void setJiraAccountId_unclaimedAccount_storesItAndPublishesOneEvent() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByJiraAccountId("jira-1")).thenReturn(Optional.empty());

        service.setJiraAccountId(1L, "jira-1");

        assertThat(user.getJiraAccountId()).isEqualTo("jira-1");
        verify(events, times(1)).publishEvent(new AuthorIdentityChangedEvent(1L));
    }

    @Test
    void setJiraAccountId_accountHeldByAnotherUser_throwsConflict() {
        otherUser.setJiraAccountId("jira-1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByJiraAccountId("jira-1")).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> service.setJiraAccountId(1L, "jira-1"))
                .isInstanceOf(ConflictException.class);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void setJiraAccountId_unchangedValue_publishesNothing() {
        user.setJiraAccountId("jira-1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.setJiraAccountId(1L, "jira-1");

        verify(events, never()).publishEvent(any());
    }

    @Test
    void claimJiraAccountIdIfAbsent_userAlreadyHasOne_isANoOp() {
        user.setJiraAccountId("jira-existing");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.claimJiraAccountIdIfAbsent(1L, "jira-new");

        assertThat(user.getJiraAccountId()).isEqualTo("jira-existing");
        verify(events, never()).publishEvent(any());
    }

    @Test
    void claimJiraAccountIdIfAbsent_accountHeldByAnotherUser_isANoOp() {
        otherUser.setJiraAccountId("jira-1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByJiraAccountId("jira-1")).thenReturn(Optional.of(otherUser));

        service.claimJiraAccountIdIfAbsent(1L, "jira-1");

        assertThat(user.getJiraAccountId()).isNull();
        verify(events, never()).publishEvent(any());
    }
}
