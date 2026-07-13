package com.juliashtal.devanalytics.ai.model;

import com.juliashtal.devanalytics.user.model.User;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * JPA entity for ai_conversations. One AI chat session owned by a user.
 */
@Data
@Entity
@Table(name = "ai_conversations")
public class AiConversationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "summary_scope", length = 32)
    private String summaryScope;

    @Column(name = "summary_context", columnDefinition = "TEXT")
    private String summaryContext;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
