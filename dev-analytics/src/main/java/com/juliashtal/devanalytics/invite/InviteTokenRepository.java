package com.juliashtal.devanalytics.invite;

import com.juliashtal.devanalytics.invite.model.InviteTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for InviteTokenEntity (invite_tokens).
 */
public interface InviteTokenRepository extends JpaRepository<InviteTokenEntity, Long> {
    Optional<InviteTokenEntity> findByToken(String token);
}
