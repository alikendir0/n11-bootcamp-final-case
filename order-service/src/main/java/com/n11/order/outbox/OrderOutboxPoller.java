package com.n11.order.outbox;

import com.n11.outbox.AbstractOutboxPoller;
import com.n11.outbox.OutboxMessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderOutboxPoller extends AbstractOutboxPoller {
    public OrderOutboxPoller(OrderOutboxRepository repo, RabbitTemplate rabbitTemplate,
                              OutboxMessagePostProcessor messagePostProcessor) {
        super(repo, rabbitTemplate, messagePostProcessor);
    }
}
