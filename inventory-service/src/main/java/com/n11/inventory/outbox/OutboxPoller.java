package com.n11.inventory.outbox;

import com.n11.outbox.AbstractOutboxPoller;
import com.n11.outbox.OutboxMessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

// concrete @Component subclass — wiring only
@Component
public class OutboxPoller extends AbstractOutboxPoller {
    public OutboxPoller(OutboxRepository outboxRepository,
                        RabbitTemplate rabbitTemplate,
                        OutboxMessagePostProcessor messagePostProcessor) {
        super(outboxRepository, rabbitTemplate, messagePostProcessor);
    }
}
