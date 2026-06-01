package com.juliashtal.devanalytics.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.ai.client.LlmClient;
import com.juliashtal.devanalytics.ai.model.*;
import com.juliashtal.devanalytics.ai.repository.AiConversationRepository;
import com.juliashtal.devanalytics.ai.repository.AiMessageRepository;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiConversationService {

    // Conversation history window — keep the last N messages to avoid exceeding model context
    private static final int MAX_HISTORY_MESSAGES = 10;

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.ollama.model:llama3}")
    private String model;

    @Transactional
    public ConversationDto startConversation(User user, String summaryScope, String summaryJson) {
        AiConversationEntity entity = new AiConversationEntity();
        entity.setUser(user);
        entity.setSummaryScope(summaryScope);
        entity.setSummaryContext(summaryJson);
        AiConversationEntity saved = conversationRepository.save(entity);

        log.info("Started AI conversation: id={}, userId={}, scope={}", saved.getId(), user.getId(), summaryScope);

        return ConversationDto.builder()
                .id(saved.getId())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional
    public MessageDto sendMessage(User user, Long conversationId, String userMessage) {
        AiConversationEntity conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NoSuchElementException("Conversation not found: " + conversationId));

        if (!conversation.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Not your conversation");
        }

        List<AiMessageEntity> allPrior = messageRepository.findByConversationOrderByCreatedAtAsc(conversation);

        // Truncate to last MAX_HISTORY_MESSAGES to avoid exceeding context window
        List<AiMessageEntity> history = allPrior.size() > MAX_HISTORY_MESSAGES
                ? allPrior.subList(allPrior.size() - MAX_HISTORY_MESSAGES, allPrior.size())
                : allPrior;

        if (history.size() < allPrior.size()) {
            log.warn("Conversation {} history truncated from {} to {} messages to fit context window",
                    conversationId, allPrior.size(), history.size());
        }

        String systemPrompt = buildFollowUpSystemPrompt(conversation.getSummaryContext());
        String fullUserPrompt = buildFollowUpUserPrompt(history, userMessage);

        log.info("Sending follow-up message: conversationId={}, historyLen={}, model={}", conversationId, history.size(), model);

        String raw = llmClient.complete(model, systemPrompt, fullUserPrompt, false);

        AiMessageEntity userMsg = new AiMessageEntity();
        userMsg.setConversation(conversation);
        userMsg.setRole("USER");
        userMsg.setContent(userMessage);
        messageRepository.save(userMsg);

        AiMessageEntity assistantMsg = new AiMessageEntity();
        assistantMsg.setConversation(conversation);
        assistantMsg.setRole("ASSISTANT");
        assistantMsg.setContent(raw.strip());
        AiMessageEntity savedAssistant = messageRepository.save(assistantMsg);

        return MessageDto.builder()
                .id(savedAssistant.getId())
                .role("ASSISTANT")
                .content(savedAssistant.getContent())
                .createdAt(savedAssistant.getCreatedAt())
                .build();
    }

    public List<MessageDto> getMessages(User user, Long conversationId) {
        AiConversationEntity conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NoSuchElementException("Conversation not found: " + conversationId));

        if (!conversation.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Not your conversation");
        }

        return messageRepository.findByConversationOrderByCreatedAtAsc(conversation).stream()
                .map(m -> MessageDto.builder()
                        .id(m.getId())
                        .role(m.getRole())
                        .content(m.getContent())
                        .createdAt(m.getCreatedAt())
                        .build())
                .toList();
    }

    private String buildFollowUpSystemPrompt(String summaryContext) {
        return """
                You are a developer analytics assistant in follow-up mode.
                The user has already seen an AI-generated summary of their metrics for a specific period.
                Answer their questions concisely using only the metrics data provided in the context below.
                Do not speculate beyond what the metrics show.
                Respond in plain conversational text — no JSON, no markdown code fences, no bullet-JSON objects.

                Summary context:
                %s
                """.formatted(formatContextAsText(summaryContext));
    }

    // Formats the stored MetricsSummaryDto JSON as readable text so the model is not tempted
    // to pattern-match on the JSON structure and echo insight objects back verbatim.
    private String formatContextAsText(String summaryContextJson) {
        if (summaryContextJson == null || summaryContextJson.isBlank()) return "(no context)";
        try {
            JsonNode root = objectMapper.readTree(summaryContextJson);
            StringBuilder sb = new StringBuilder();

            String headline = root.path("headline").asText("").strip();
            if (!headline.isEmpty()) sb.append("Headline: ").append(headline).append("\n\n");

            String overview = root.path("overview").asText("").strip();
            if (!overview.isEmpty()) sb.append("Overview: ").append(overview).append("\n\n");

            JsonNode insights = root.path("insights");
            if (insights.isArray() && !insights.isEmpty()) {
                sb.append("Insights:\n");
                for (JsonNode node : insights) {
                    String kind   = node.path("kind").asText("note");
                    String metric = node.path("metric").asText("").strip();
                    String text   = node.path("text").asText("").strip();
                    sb.append("- [").append(kind).append("] ");
                    if (!metric.isEmpty()) sb.append(metric).append(": ");
                    sb.append(text).append("\n");
                }
                sb.append("\n");
            }

            JsonNode recs = root.path("recommendations");
            if (recs.isArray() && !recs.isEmpty()) {
                sb.append("Recommendations:\n");
                for (JsonNode rec : recs) {
                    sb.append("- ").append(rec.asText()).append("\n");
                }
            }

            String result = sb.toString().strip();
            return result.isEmpty() ? "(no context)" : result;
        } catch (Exception e) {
            log.warn("Failed to parse summaryContext JSON for follow-up prompt, using raw text: {}", e.getMessage());
            return summaryContextJson;
        }
    }

    private String buildFollowUpUserPrompt(List<AiMessageEntity> history, String newMessage) {
        if (history.isEmpty()) {
            return newMessage;
        }
        StringBuilder sb = new StringBuilder("Conversation history:\n");
        for (AiMessageEntity msg : history) {
            sb.append(msg.getRole()).append(": ").append(msg.getContent()).append('\n');
        }
        sb.append("\nNew question:\n").append(newMessage);
        return sb.toString();
    }
}
