package com.n11.ai.infrastructure.persistence;

import com.n11.ai.domain.chat.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository("aiMessageRepository")
public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByConversationIdOrderBySequenceNoAsc(UUID conversationId);
}
