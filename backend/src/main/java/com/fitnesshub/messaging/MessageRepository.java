package com.fitnesshub.messaging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    Page<Message> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    List<Message> findByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    long countByConversationIdAndSenderIdNotAndReadAtIsNull(UUID conversationId, UUID notSenderId);

    List<Message> findByConversationIdInAndReadAtIsNull(List<UUID> conversationIds);

    /** The newest message in each conversation, one row per conversation. */
    @Query(value = """
            select distinct on (conversation_id) *
            from messages
            where conversation_id in (:conversationIds)
            order by conversation_id, created_at desc
            """, nativeQuery = true)
    List<Message> findLatestPerConversation(@Param("conversationIds") List<UUID> conversationIds);

    /** Every unread message addressed to this coach, across all of their conversations. */
    @Query("""
            select count(m)
            from Message m, Conversation c
            where m.conversationId = c.id
              and c.coachId = :coachId
              and m.senderId <> :coachId
              and m.readAt is null
            """)
    long countUnreadForCoach(@Param("coachId") UUID coachId);

    /** Unread counts for many conversations at once. Columns: conversation_id, unread. */
    @Query("""
            select m.conversationId, count(m)
            from Message m
            where m.conversationId in :conversationIds
              and m.senderId <> :readerId
              and m.readAt is null
            group by m.conversationId
            """)
    List<Object[]> countUnreadPerConversation(@Param("conversationIds") List<UUID> conversationIds,
                                              @Param("readerId") UUID readerId);
}
