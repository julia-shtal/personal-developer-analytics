package com.juliashtal.devanalytics.messaging;

import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.messaging.dto.DirectMessageDto;
import com.juliashtal.devanalytics.messaging.dto.UnreadCountDto;
import com.juliashtal.devanalytics.messaging.model.MessageEntity;
import com.juliashtal.devanalytics.messaging.repository.MessageRepository;
import com.juliashtal.devanalytics.messaging.service.MessageService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.sql.Timestamp;
import com.juliashtal.devanalytics.messaging.dto.InboxEntryDto;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock MessageRepository messageRepository;
    @Mock UserRepository userRepository;
    @Mock TeamRepository teamRepository;
    @InjectMocks MessageService service;

    private User sender;
    private User recipient;

    @BeforeEach
    void setUp() {
        sender = new User();
        sender.setId(1L);
        sender.setEmail("a@example.com");

        recipient = new User();
        recipient.setId(2L);
        recipient.setEmail("b@example.com");
    }

    // ── send ──────────────────────────────────────────────────────────────────

    @Test
    void send_toNonTeammate_throwsForbidden() {
        when(teamRepository.shareTeam(1L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> service.send(sender, 2L, "Hello"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void send_toTeammate_savesAndReturnsDto() {
        when(teamRepository.shareTeam(1L, 2L)).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));

        MessageEntity saved = new MessageEntity();
        saved.setId(10L);
        saved.setSender(sender);
        saved.setRecipient(recipient);
        saved.setBody("Hello");
        saved.setCreatedAt(Instant.now());

        when(messageRepository.save(any())).thenReturn(saved);

        DirectMessageDto dto = service.send(sender, 2L, "Hello");

        assertThat(dto.body()).isEqualTo("Hello");
        assertThat(dto.senderId()).isEqualTo(1L);
        assertThat(dto.recipientId()).isEqualTo(2L);
    }

    @Test
    void send_stripsWhitespace() {
        when(teamRepository.shareTeam(1L, 2L)).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));

        MessageEntity saved = new MessageEntity();
        saved.setId(11L);
        saved.setSender(sender);
        saved.setRecipient(recipient);
        saved.setBody("trimmed");
        saved.setCreatedAt(Instant.now());
        when(messageRepository.save(any())).thenReturn(saved);

        service.send(sender, 2L, "  trimmed  ");

        verify(messageRepository).save(any());
    }

    // ── conversation ──────────────────────────────────────────────────────────

    @Test
    void conversation_marksIncomingMessagesAsRead() {
        when(messageRepository.findConversation(eq(1L), eq(2L), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.conversation(sender, 2L, 0, 50);

        verify(messageRepository).markRead(eq(1L), eq(2L), any(Instant.class));
    }

    @Test
    void conversation_returnsMessagesNewestFirst() {
        MessageEntity m1 = new MessageEntity();
        m1.setId(1L); m1.setSender(sender); m1.setRecipient(recipient);
        m1.setBody("first"); m1.setCreatedAt(Instant.now());

        MessageEntity m2 = new MessageEntity();
        m2.setId(2L); m2.setSender(recipient); m2.setRecipient(sender);
        m2.setBody("reply"); m2.setCreatedAt(Instant.now());

        when(messageRepository.findConversation(eq(1L), eq(2L), any()))
                .thenReturn(new PageImpl<>(List.of(m2, m1)));

        List<DirectMessageDto> result = service.conversation(sender, 2L, 0, 50);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).body()).isEqualTo("reply");
    }

    // ── unreadCount ───────────────────────────────────────────────────────────

    @Test
    void unreadCount_returnsCorrectCount() {
        when(messageRepository.countUnread(1L)).thenReturn(5L);

        UnreadCountDto result = service.unreadCount(sender);

        assertThat(result.count()).isEqualTo(5L);
    }

    @Test
    void unreadCount_zeroWhenNoUnread() {
        when(messageRepository.countUnread(1L)).thenReturn(0L);

        assertThat(service.unreadCount(sender).count()).isZero();
    }

    // ── inbox ─────────────────────────────────────────────────────────────────

    @Test
    void inbox_mapsRawRowsToInboxEntries() {
        Object[] row = new Object[] {
                2L,                                                      // partnerId
                "bob",                                                   // partnerUsername
                "preset-02",                                             // partnerAvatarPreset
                true,                                                    // partnerHasCustomAvatar
                "see you tomorrow",                                      // lastBody
                Timestamp.from(Instant.parse("2024-01-15T10:00:00Z")),   // lastMessageAt
                2L,                                                      // lastSenderId
                3L                                                       // unreadCount
        };
        List<Object[]> rows = new java.util.ArrayList<>();
        rows.add(row);
        when(messageRepository.findInboxRaw(1L)).thenReturn(rows);

        List<InboxEntryDto> result = service.inbox(sender);

        assertThat(result).hasSize(1);
        InboxEntryDto entry = result.get(0);
        assertThat(entry.partnerId()).isEqualTo(2L);
        assertThat(entry.partnerUsername()).isEqualTo("bob");
        assertThat(entry.partnerAvatarPreset()).isEqualTo("preset-02");
        assertThat(entry.partnerHasCustomAvatar()).isTrue();
        assertThat(entry.lastBody()).isEqualTo("see you tomorrow");
        assertThat(entry.lastMessageAt()).isEqualTo(Instant.parse("2024-01-15T10:00:00Z"));
        assertThat(entry.lastSenderId()).isEqualTo(2L);
        assertThat(entry.unreadCount()).isEqualTo(3L);
    }

    @Test
    void inbox_noConversations_returnsEmptyList() {
        when(messageRepository.findInboxRaw(1L)).thenReturn(List.of());

        assertThat(service.inbox(sender)).isEmpty();
    }
}
