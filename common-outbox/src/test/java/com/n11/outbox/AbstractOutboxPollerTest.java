package com.n11.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractOutboxPollerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    static final class TestPoller extends AbstractOutboxPoller {
        TestPoller(OutboxRepository repo, RabbitTemplate t, OutboxMessagePostProcessor pp) {
            super(repo, t, pp);
        }
    }

    @Test
    void poll_publishesEachUnsentEvent_andMarksSent() {
        OutboxRepository repo = mock(OutboxRepository.class);
        RabbitTemplate t = mock(RabbitTemplate.class);
        OutboxMessagePostProcessor pp = new OutboxMessagePostProcessor(mapper);
        OutboxEvent e1 = new OutboxEvent(UUID.randomUUID(), "orders", "order.created",
            "{\"eventId\":\"" + UUID.randomUUID() + "\",\"correlationId\":\"" + UUID.randomUUID() + "\"}",
            Instant.now());
        when(repo.findUnsentBatch(anyInt())).thenReturn(List.of(e1));

        new TestPoller(repo, t, pp).poll();

        verify(t).convertAndSend(eq("orders.tx"), eq("order.created"), eq(e1.getPayload()), any(MessagePostProcessor.class));
        assertThat(e1.getSentAt()).isNotNull();
        verify(repo).save(e1);
    }

    @Test
    void poll_isNoOp_whenBatchEmpty() {
        OutboxRepository repo = mock(OutboxRepository.class);
        RabbitTemplate t = mock(RabbitTemplate.class);
        OutboxMessagePostProcessor pp = new OutboxMessagePostProcessor(mapper);
        when(repo.findUnsentBatch(anyInt())).thenReturn(List.of());

        new TestPoller(repo, t, pp).poll();

        verify(t, org.mockito.Mockito.never()).convertAndSend(eq(""), eq(""), eq(""), any(MessagePostProcessor.class));
    }
}
