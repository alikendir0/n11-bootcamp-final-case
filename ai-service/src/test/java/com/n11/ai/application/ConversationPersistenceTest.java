package com.n11.ai.application;

import com.n11.ai.AiServiceApplication;
import com.n11.ai.infrastructure.persistence.ConversationRepository;
import com.n11.ai.infrastructure.persistence.MessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AiServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@TestPropertySource(properties = {
    "ai.provider=echo",
    "spring.cloud.config.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "eureka.client.enabled=false",
    "spring.flyway.locations=classpath:db/migration/ai",
    "spring.flyway.schemas=public",
    "spring.flyway.default-schema=public",
    "spring.jpa.properties.hibernate.default_schema=public",
    "spring.datasource.hikari.connection-init-sql=CREATE EXTENSION IF NOT EXISTS pgcrypto"
})
class ConversationPersistenceTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired ConversationStateService stateService;
    @Autowired ConversationRepository conversations;
    @Autowired MessageRepository messages;

    @Test
    void authed_conversation_persists_user_message() {
        UUID convId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ConversationStore store = stateService.open(convId, userId);
        store.appendUserMessage("merhaba");

        assertThat(conversations.findById(convId)).isPresent();
        assertThat(messages.findByConversationIdOrderBySequenceNoAsc(convId)).hasSize(1);
        assertThat(messages.findByConversationIdOrderBySequenceNoAsc(convId).get(0).getContent())
            .isEqualTo("merhaba");
    }

    @Test
    void guest_conversation_does_not_touch_db() {
        UUID convId = UUID.randomUUID();
        long before = conversations.count();
        ConversationStore store = stateService.open(convId, null);   // null userId = guest
        store.appendUserMessage("merhaba");

        assertThat(conversations.count()).isEqualTo(before);
    }
}
