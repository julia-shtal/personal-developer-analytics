package com.juliashtal.devanalytics.ai.repository;

import com.juliashtal.devanalytics.ai.model.AiConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for AiConversationEntity (ai_conversations).
 */
public interface AiConversationRepository extends JpaRepository<AiConversationEntity, Long> {
}
