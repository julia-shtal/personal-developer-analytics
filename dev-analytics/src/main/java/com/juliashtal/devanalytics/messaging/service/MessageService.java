package com.juliashtal.devanalytics.messaging.service;

import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.exception.NotFoundException;
import com.juliashtal.devanalytics.messaging.dto.DirectMessageDto;
import com.juliashtal.devanalytics.messaging.dto.InboxEntryDto;
import com.juliashtal.devanalytics.messaging.dto.UnreadCountDto;
import com.juliashtal.devanalytics.messaging.model.MessageEntity;
import com.juliashtal.devanalytics.messaging.repository.MessageRepository;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    public DirectMessageDto send(User sender, Long recipientId, String body) {
        if (!teamRepository.shareTeam(sender.getId(), recipientId)) {
            throw new ForbiddenException("You can only message users who share a team with you");
        }
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new NotFoundException("User not found: " + recipientId));

        MessageEntity msg = new MessageEntity();
        msg.setSender(sender);
        msg.setRecipient(recipient);
        msg.setBody(body.strip());
        return toDto(messageRepository.save(msg));
    }

    @Transactional
    public List<DirectMessageDto> conversation(User me, Long otherUserId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        List<DirectMessageDto> messages = messageRepository
                .findConversation(me.getId(), otherUserId, pageable)
                .stream().map(this::toDto).toList();
        messageRepository.markRead(me.getId(), otherUserId, Instant.now());
        return messages;
    }

    public List<InboxEntryDto> inbox(User me) {
        return messageRepository.findInboxRaw(me.getId())
                .stream().map(this::mapInboxRow).toList();
    }

    public UnreadCountDto unreadCount(User me) {
        return new UnreadCountDto(messageRepository.countUnread(me.getId()));
    }

    private InboxEntryDto mapInboxRow(Object[] row) {
        Long partnerId              = ((Number) row[0]).longValue();
        String partnerUsername      = (String) row[1];
        String partnerAvatarPreset  = (String) row[2];
        boolean partnerHasCustomAvatar = (Boolean) row[3];
        String lastBody             = (String) row[4];
        Instant lastMessageAt       = ((Timestamp) row[5]).toInstant();
        Long lastSenderId           = ((Number) row[6]).longValue();
        long unreadCount            = ((Number) row[7]).longValue();
        return new InboxEntryDto(partnerId, partnerUsername, partnerAvatarPreset,
                partnerHasCustomAvatar, lastBody, lastMessageAt, lastSenderId, unreadCount);
    }

    private DirectMessageDto toDto(MessageEntity m) {
        return new DirectMessageDto(
                m.getId(),
                m.getSender().getId(),
                m.getRecipient().getId(),
                m.getBody(),
                m.getCreatedAt(),
                m.getReadAt()
        );
    }
}
