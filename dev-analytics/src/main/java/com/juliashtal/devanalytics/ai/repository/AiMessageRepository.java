package com.juliashtal.devanalytics.ai.repository;

import com.juliashtal.devanalytics.ai.model.AiConversationEntity;
import com.juliashtal.devanalytics.ai.model.AiMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiMessageRepository extends JpaRepository<AiMessageEntity, Long> {
    List<AiMessageEntity> findByConversationOrderByCreatedAtAsc(AiConversationEntity conversation);
}
