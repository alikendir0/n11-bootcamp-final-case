package com.n11.ai.domain.chat;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity(name = "AiMessage")
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MessageRoleEntity role;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "tool_call_json", columnDefinition = "TEXT")
    private String toolCallJson;

    @Column(name = "tool_result_json", columnDefinition = "TEXT")
    private String toolResultJson;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Message() {}

    public Message(UUID conversationId, MessageRoleEntity role, String content,
                   String toolCallJson, String toolResultJson, int sequenceNo) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.toolCallJson = toolCallJson;
        this.toolResultJson = toolResultJson;
        this.sequenceNo = sequenceNo;
    }

    public UUID getId() { return id; }
    public UUID getConversationId() { return conversationId; }
    public MessageRoleEntity getRole() { return role; }
    public String getContent() { return content; }
    public String getToolCallJson() { return toolCallJson; }
    public String getToolResultJson() { return toolResultJson; }
    public int getSequenceNo() { return sequenceNo; }
    public Instant getCreatedAt() { return createdAt; }
}
