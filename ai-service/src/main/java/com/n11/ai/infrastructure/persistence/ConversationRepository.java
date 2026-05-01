package com.n11.ai.infrastructure.persistence;

import com.n11.ai.domain.chat.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Repository("aiConversationRepository")
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * Directly update timestamps + seenIdsJson without requiring a managed entity.
     * Called by ConversationStateService.AuthedStore.touch() to avoid detached-entity
     * issues when the store is used across multiple short-lived transactions.
     */
    @Modifying
    @Transactional
    @Query("UPDATE AiConversation c SET c.updatedAt = :now, c.lastActivityAt = :now, " +
           "c.seenIdsJson = :seenIdsJson WHERE c.id = :id")
    void touchAndUpdateSeenIds(@Param("id") UUID id, @Param("now") Instant now,
                                @Param("seenIdsJson") String seenIdsJson);

    /**
     * Insert a new conversation with a client-assigned UUID using native SQL.
     * Avoids Spring Data JPA's merge() path (which requires a DB SELECT first)
     * when ID is set manually before first save.
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO ai_conversations (id, user_id, created_at, updated_at, last_activity_at) " +
                   "VALUES (:id, :userId, :now, :now, :now) ON CONFLICT (id) DO NOTHING",
           nativeQuery = true)
    void insertIfAbsent(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") Instant now);
}
