package com.n11.inventory.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Drains unsent outbox rows to RabbitMQ every 5 seconds (CD-04). Marks each
 * row {@code sent_at = now()} after a successful publish in the SAME transaction.
 *
 * <p>{@code @Scheduled(fixedDelay)} ensures the next run starts only AFTER the
 * previous run completes — no overlap. {@code FOR UPDATE SKIP LOCKED} (in the
 * repo query) ensures even if two pollers were ever scaled out, neither would
 * double-publish a row.
 *
 * <p>Failure handling: on RabbitMQ broker outage, the publish call throws
 * AmqpException, the {@code @Transactional} rolls back the {@code sent_at}
 * update, the rows stay {@code sent_at IS NULL}, and the next poll retries.
 */
@Component
public class OutboxPoller {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 100;  // CD-04

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPoller(OutboxRepository outboxRepository, RabbitTemplate rabbitTemplate) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelay = 5000)   // CD-04: every 5 seconds
    @Transactional
    public void poll() {
        List<OutboxEvent> unsent = outboxRepository.findUnsentBatch(BATCH_SIZE);
        if (unsent.isEmpty()) {
            return;
        }
        LOG.debug("outbox.poller draining {} unsent events", unsent.size());

        for (OutboxEvent event : unsent) {
            String exchange = event.getAggregate() + ".tx"; // "inventory.tx"
            rabbitTemplate.convertAndSend(
                    exchange,
                    event.getEventType(),     // routing key e.g. "stock.reserved"
                    event.getPayload()        // envelope JSON as String
            );
            event.markSent(Instant.now());
            outboxRepository.save(event);
        }
    }
}
