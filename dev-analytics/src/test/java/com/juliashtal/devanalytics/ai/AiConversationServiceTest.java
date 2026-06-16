package com.juliashtal.devanalytics.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.ai.client.LlmClient;
import com.juliashtal.devanalytics.ai.model.AiConversationEntity;
import com.juliashtal.devanalytics.ai.model.AiMessageEntity;
import com.juliashtal.devanalytics.ai.model.ConversationDto;
import com.juliashtal.devanalytics.ai.model.MessageDto;
import com.juliashtal.devanalytics.ai.repository.AiConversationRepository;
import com.juliashtal.devanalytics.ai.repository.AiMessageRepository;
import com.juliashtal.devanalytics.ai.service.AiConversationService;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiConversationServiceTest {

    @Mock AiConversationRepository conversationRepository;
    @Mock AiMessageRepository messageRepository;
    @Mock LlmClient llmClient;

    AiConversationService service;

    private User user;

    @BeforeEach
    void setUp() {
        service = new AiConversationService(conversationRepository, messageRepository, llmClient,
                new ObjectMapper());
        ReflectionTestUtils.setField(service, "model", "llama3.2");

        user = new User();
        user.setId(1L);
    }

    // ── startConversation ────────────────────────────────────────────────────

    @Test
    void startConversation_savesEntityAndReturnsDto() {
        Instant now = Instant.now();
        when(conversationRepository.save(any(AiConversationEntity.class))).thenAnswer(inv -> {
            AiConversationEntity e = inv.getArgument(0);
            e.setId(10L);
            e.setCreatedAt(now);
            return e;
        });

        ConversationDto dto = service.startConversation(user, "PERSONAL", "{\"headline\":\"Great week\"}");

        assertThat(dto.id()).isEqualTo(10L);
        assertThat(dto.createdAt()).isEqualTo(now);

        ArgumentCaptor<AiConversationEntity> captor = ArgumentCaptor.forClass(AiConversationEntity.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getSummaryScope()).isEqualTo("PERSONAL");
        assertThat(captor.getValue().getSummaryContext()).isEqualTo("{\"headline\":\"Great week\"}");
    }

    // ── sendMessage ──────────────────────────────────────────────────────────

    @Test
    void sendMessage_happyPath_savesUserAndAssistantMessagesAndUsesRawPromptForEmptyHistory() {
        AiConversationEntity conversation = new AiConversationEntity();
        conversation.setId(5L);
        conversation.setUser(user);
        conversation.setSummaryContext("""
                {"headline":"Great week","overview":"Solid progress.",
                 "insights":[{"kind":"positive","metric":"PR Lead Time","text":"improved"}],
                 "recommendations":["Keep reviewing PRs promptly"]}
                """);
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationOrderByCreatedAtAsc(conversation)).thenReturn(List.of());
        when(llmClient.complete(eq("llama3.2"), anyString(), anyString(), eq(false)))
                .thenReturn("  Sure, here's more detail.  ");

        AtomicLong idSeq = new AtomicLong(100);
        when(messageRepository.save(any(AiMessageEntity.class))).thenAnswer(inv -> {
            AiMessageEntity m = inv.getArgument(0);
            m.setId(idSeq.incrementAndGet());
            m.setCreatedAt(Instant.now());
            return m;
        });

        MessageDto result = service.sendMessage(user, 5L, "What about PR lead time?");

        assertThat(result.role()).isEqualTo("ASSISTANT");
        assertThat(result.content()).isEqualTo("Sure, here's more detail.");
        assertThat(result.id()).isEqualTo(102L);

        ArgumentCaptor<AiMessageEntity> savedCaptor = ArgumentCaptor.forClass(AiMessageEntity.class);
        verify(messageRepository, times(2)).save(savedCaptor.capture());
        List<AiMessageEntity> saved = savedCaptor.getAllValues();
        assertThat(saved.get(0).getRole()).isEqualTo("USER");
        assertThat(saved.get(0).getContent()).isEqualTo("What about PR lead time?");
        assertThat(saved.get(1).getRole()).isEqualTo("ASSISTANT");

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).complete(eq("llama3.2"), systemPromptCaptor.capture(), userPromptCaptor.capture(), eq(false));

        String systemPrompt = systemPromptCaptor.getValue();
        assertThat(systemPrompt).contains("Headline: Great week");
        assertThat(systemPrompt).contains("Overview: Solid progress.");
        assertThat(systemPrompt).contains("Insights:");
        assertThat(systemPrompt).contains("[positive] PR Lead Time: improved");
        assertThat(systemPrompt).contains("Recommendations:");
        assertThat(systemPrompt).contains("- Keep reviewing PRs promptly");

        // Empty history -> buildFollowUpUserPrompt returns the raw message unchanged
        assertThat(userPromptCaptor.getValue()).isEqualTo("What about PR lead time?");
    }

    @Test
    void sendMessage_conversationNotFound_throwsNoSuchElement() {
        when(conversationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendMessage(user, 99L, "hi"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void sendMessage_notOwner_throwsForbidden() {
        AiConversationEntity conversation = new AiConversationEntity();
        conversation.setId(5L);
        User otherUser = new User();
        otherUser.setId(2L);
        conversation.setUser(otherUser);
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.sendMessage(user, 5L, "hi"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void sendMessage_historyOver10Messages_truncatesToLast10() {
        AiConversationEntity conversation = new AiConversationEntity();
        conversation.setId(5L);
        conversation.setUser(user);
        conversation.setSummaryContext(null);
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));

        List<AiMessageEntity> history = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            AiMessageEntity m = new AiMessageEntity();
            m.setRole(i % 2 == 1 ? "USER" : "ASSISTANT");
            m.setContent("msg-" + i);
            history.add(m);
        }
        when(messageRepository.findByConversationOrderByCreatedAtAsc(conversation)).thenReturn(history);
        when(llmClient.complete(anyString(), anyString(), anyString(), eq(false))).thenReturn("ok");
        when(messageRepository.save(any(AiMessageEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.sendMessage(user, 5L, "new question");

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).complete(anyString(), systemPromptCaptor.capture(), userPromptCaptor.capture(), eq(false));

        // null summaryContext -> "(no context)" placeholder
        assertThat(systemPromptCaptor.getValue()).contains("(no context)");

        String userPrompt = userPromptCaptor.getValue();
        assertThat(userPrompt).doesNotContain("msg-1\n").doesNotContain("msg-2\n");
        assertThat(userPrompt).contains("msg-3").contains("msg-12");
        assertThat(userPrompt).contains("New question:\nnew question");
    }

    @Test
    void sendMessage_malformedSummaryContextJson_fallsBackToRawText() {
        AiConversationEntity conversation = new AiConversationEntity();
        conversation.setId(5L);
        conversation.setUser(user);
        conversation.setSummaryContext("not valid json{");
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationOrderByCreatedAtAsc(conversation)).thenReturn(List.of());
        when(llmClient.complete(anyString(), anyString(), anyString(), eq(false))).thenReturn("ok");
        when(messageRepository.save(any(AiMessageEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.sendMessage(user, 5L, "hi");

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).complete(anyString(), systemPromptCaptor.capture(), anyString(), eq(false));
        assertThat(systemPromptCaptor.getValue()).contains("not valid json{");
    }

    @Test
    void sendMessage_jsonContextWithNoRecognizedFields_usesNoContextPlaceholder() {
        AiConversationEntity conversation = new AiConversationEntity();
        conversation.setId(5L);
        conversation.setUser(user);
        conversation.setSummaryContext("{}");
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationOrderByCreatedAtAsc(conversation)).thenReturn(List.of());
        when(llmClient.complete(anyString(), anyString(), anyString(), eq(false))).thenReturn("ok");
        when(messageRepository.save(any(AiMessageEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.sendMessage(user, 5L, "hi");

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).complete(anyString(), systemPromptCaptor.capture(), anyString(), eq(false));
        assertThat(systemPromptCaptor.getValue()).contains("(no context)");
    }

    // ── getMessages ──────────────────────────────────────────────────────────

    @Test
    void getMessages_happyPath_returnsMappedDtos() {
        AiConversationEntity conversation = new AiConversationEntity();
        conversation.setId(5L);
        conversation.setUser(user);
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));

        AiMessageEntity m1 = new AiMessageEntity();
        m1.setId(1L);
        m1.setRole("USER");
        m1.setContent("hi");
        m1.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));

        AiMessageEntity m2 = new AiMessageEntity();
        m2.setId(2L);
        m2.setRole("ASSISTANT");
        m2.setContent("hello");
        m2.setCreatedAt(Instant.parse("2024-01-01T00:01:00Z"));

        when(messageRepository.findByConversationOrderByCreatedAtAsc(conversation)).thenReturn(List.of(m1, m2));

        List<MessageDto> result = service.getMessages(user, 5L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).role()).isEqualTo("USER");
        assertThat(result.get(1).content()).isEqualTo("hello");
    }

    @Test
    void getMessages_conversationNotFound_throwsNoSuchElement() {
        when(conversationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMessages(user, 99L))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getMessages_notOwner_throwsForbidden() {
        AiConversationEntity conversation = new AiConversationEntity();
        conversation.setId(5L);
        User otherUser = new User();
        otherUser.setId(99L);
        conversation.setUser(otherUser);
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.getMessages(user, 5L))
                .isInstanceOf(ForbiddenException.class);
    }
}
