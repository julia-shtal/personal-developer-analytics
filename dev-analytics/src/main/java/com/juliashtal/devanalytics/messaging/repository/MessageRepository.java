package com.juliashtal.devanalytics.messaging.repository;

import com.juliashtal.devanalytics.messaging.model.MessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data repository for MessageEntity (messages). Conversation and unread-count queries.
 */
public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

    @Query("SELECT m FROM MessageEntity m WHERE " +
           "(m.sender.id = :me AND m.recipient.id = :other) OR " +
           "(m.sender.id = :other AND m.recipient.id = :me) " +
           "ORDER BY m.createdAt DESC")
    Page<MessageEntity> findConversation(@Param("me") Long me, @Param("other") Long other, Pageable pageable);

    @Modifying
    @Query("UPDATE MessageEntity m SET m.readAt = :now " +
           "WHERE m.recipient.id = :me AND m.sender.id = :other AND m.readAt IS NULL")
    void markRead(@Param("me") Long me, @Param("other") Long other, @Param("now") Instant now);

    @Query("SELECT COUNT(m) FROM MessageEntity m WHERE m.recipient.id = :me AND m.readAt IS NULL")
    long countUnread(@Param("me") Long me);

    /**
     * Returns one row per conversation partner: latest message preview + unread count.
     * Uses DISTINCT ON (PostgreSQL) to select the most-recent message per partner efficiently.
     * Columns: partner_id, partner_username, partner_avatar_preset, partner_has_custom_avatar,
     *          last_body, last_message_at, last_sender_id, unread_count
     */
    @Query(value = """
            WITH latest_per_partner AS (
                SELECT DISTINCT ON (partner_id)
                    CASE WHEN sender_id = :me THEN recipient_id ELSE sender_id END AS partner_id,
                    body     AS last_body,
                    created_at AS last_message_at,
                    sender_id  AS last_sender_id
                FROM messages
                WHERE sender_id = :me OR recipient_id = :me
                ORDER BY partner_id, created_at DESC
            )
            SELECT
                lpp.partner_id,
                u.username             AS partner_username,
                u.avatar_preset        AS partner_avatar_preset,
                (u.avatar_data IS NOT NULL) AS partner_has_custom_avatar,
                lpp.last_body,
                lpp.last_message_at,
                lpp.last_sender_id,
                COUNT(unread.id)       AS unread_count
            FROM latest_per_partner lpp
            JOIN users u ON u.id = lpp.partner_id
            LEFT JOIN messages unread
                   ON unread.recipient_id = :me
                  AND unread.sender_id    = lpp.partner_id
                  AND unread.read_at IS NULL
            GROUP BY lpp.partner_id, u.username, u.avatar_preset,
                     (u.avatar_data IS NOT NULL),
                     lpp.last_body, lpp.last_message_at, lpp.last_sender_id
            ORDER BY lpp.last_message_at DESC
            """, nativeQuery = true)
    List<Object[]> findInboxRaw(@Param("me") Long me);
}
