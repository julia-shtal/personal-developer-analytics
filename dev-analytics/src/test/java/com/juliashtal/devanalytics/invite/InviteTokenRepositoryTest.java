package com.juliashtal.devanalytics.invite;

import com.juliashtal.devanalytics.invite.model.InviteTokenEntity;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that the invite table is purged on the same terms as the other token tables: a row is
 * disposable once it has expired or been redeemed, and a live unredeemed row survives.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InviteTokenRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-03-15T02:00:00Z");

    @Autowired InviteTokenRepository repository;
    @Autowired EntityManager         entityManager;

    private User creator;

    @BeforeEach
    void persistCreator() {
        creator = new User();
        creator.setEmail("creator@example.com");
        creator.setUsername("creator");
        creator.setPasswordHash("hash");
        creator.setRole(Role.MANAGER);
        creator.setTimezone("UTC");
        entityManager.persist(creator);
    }

    private InviteTokenEntity invite(String token, Instant expiresAt, Instant usedAt) {
        InviteTokenEntity e = new InviteTokenEntity();
        e.setToken(token);
        e.setEmail(token + "@example.com");
        e.setRole(Role.DEVELOPER);
        e.setCreatedBy(creator);
        e.setExpiresAt(expiresAt);
        e.setUsedAt(usedAt);
        entityManager.persist(e);
        return e;
    }

    private List<String> remainingTokens() {
        entityManager.flush();
        entityManager.clear();
        return repository.findAll().stream().map(InviteTokenEntity::getToken).sorted().toList();
    }

    @Test
    void deleteExpiredOrRedeemed_expiredRow_isRemoved() {
        invite("expired", NOW.minusSeconds(1), null);
        invite("live",    NOW.plusSeconds(3600), null);

        repository.deleteExpiredOrRedeemed(NOW);

        assertThat(remainingTokens()).containsExactly("live");
    }

    @Test
    void deleteExpiredOrRedeemed_redeemedButUnexpiredRow_isRemoved() {
        invite("redeemed", NOW.plusSeconds(3600), NOW.minusSeconds(60));
        invite("live",     NOW.plusSeconds(3600), null);

        repository.deleteExpiredOrRedeemed(NOW);

        assertThat(remainingTokens()).containsExactly("live");
    }

    @Test
    void deleteExpiredOrRedeemed_liveUnredeemedRow_survives() {
        invite("live", NOW.plusSeconds(3600), null);

        repository.deleteExpiredOrRedeemed(NOW);

        assertThat(remainingTokens()).containsExactly("live");
    }

    @Test
    void deleteExpiredOrRedeemed_rowExpiringExactlyNow_survives() {
        invite("boundary", NOW, null);

        repository.deleteExpiredOrRedeemed(NOW);

        assertThat(remainingTokens()).containsExactly("boundary");
    }
}
