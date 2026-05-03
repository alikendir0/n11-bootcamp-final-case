package com.n11.product.product.outbox;

import com.n11.outbox.AbstractOutboxPoller;
import com.n11.outbox.OutboxMessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProductOutboxPoller extends AbstractOutboxPoller {

    public ProductOutboxPoller(OutboxRepository outboxRepository,
                               RabbitTemplate rabbitTemplate,
                               OutboxMessagePostProcessor messagePostProcessor) {
        super(outboxRepository, rabbitTemplate, messagePostProcessor);
    }
}
