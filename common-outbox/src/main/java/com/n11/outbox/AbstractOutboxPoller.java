package com.n11.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Shared scheduled driver for the transactional outbox pattern (saga-contracts.md §5).
 *
 * <p>Concrete subclasses (one per service that publishes saga events) must only provide
 * a constructor that wires the per-service {@link OutboxRepository} implementation.
 * All drain/publish/mark-sent logic lives here — preventing per-service copy-paste and
 * the messageId-setter regression risk (Phase 4 Plan 04-03 retrospective, commit 06338b1).
 *
 * <p>{@code @Scheduled(fixedDelay = 5000)} ensures the next run starts only AFTER the
 * previous run completes — no overlap. {@code FOR UPDATE SKIP LOCKED} (in the per-service
 * repo query) ensures two poller instances scaled out cannot double-publish the same row.
 *
 * <p>The {@link OutboxMessagePostProcessor} injected here sets
 * {@code MessageProperties.setMessageId(eventId)} and
 * {@code MessageProperties.setCorrelationId(correlationId)} on every AMQP message
 * by reading the envelope JSON in the message body. This satisfies the consumer-side
 * {@code RabbitRetryConfig.sagaRetryInterceptor.messageKeyGenerator} invariant uniformly
 * across ALL outbox publishers (D-09).
 *
 * <p>Failure handling: if the RabbitMQ broker is unavailable, the publish call throws
 * {@code AmqpException}, the {@code @Transactional} rolls back the {@code sent_at}
 * update, rows stay {@code sent_at IS NULL}, and the next poll retries automatically.
 */
public abstract class AbstractOutboxPoller {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractOutboxPoller.class);
    protected static final int DEFAULT_BATCH_SIZE = 100;

    protected final OutboxRepository outboxRepository;
    protected final RabbitTemplate rabbitTemplate;
    protected final OutboxMessagePostProcessor messagePostProcessor;

    protected AbstractOutboxPoller(OutboxRepository outboxRepository,
                                   RabbitTemplate rabbitTemplate,
                                   OutboxMessagePostProcessor messagePostProcessor) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.messagePostProcessor = messagePostProcessor;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void poll() {
        List<OutboxEvent> unsent = outboxRepository.findUnsentBatch(DEFAULT_BATCH_SIZE);
        if (unsent.isEmpty()) return;
        LOG.debug("outbox.poller draining {} unsent events", unsent.size());
        for (OutboxEvent event : unsent) {
            String exchange = event.getAggregate() + ".tx";
            rabbitTemplate.convertAndSend(exchange, event.getEventType(), event.getPayload(), messagePostProcessor);
            event.markSent(Instant.now());
            outboxRepository.save(event);
        }
    }
}
