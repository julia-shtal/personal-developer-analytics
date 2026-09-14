package com.juliashtal.devanalytics.invite;

import com.juliashtal.devanalytics.invite.model.InviteTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

/**
 * Spring Data repository for InviteTokenEntity (invite_tokens). Purges expired or redeemed invites.
 */
public interface InviteTokenRepository extends JpaRepository<InviteTokenEntity, Long> {

    Optional<InviteTokenEntity> findByToken(String token);

    @Modifying
    @Query("DELETE FROM InviteTokenEntity i WHERE i.expiresAt < :now OR i.usedAt IS NOT NULL")
    void deleteExpiredOrRedeemed(Instant now);
}
