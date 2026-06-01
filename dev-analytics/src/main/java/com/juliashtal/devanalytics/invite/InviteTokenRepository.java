package com.juliashtal.devanalytics.invite;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InviteTokenRepository extends JpaRepository<InviteTokenEntity, Long> {
    Optional<InviteTokenEntity> findByToken(String token);
}
