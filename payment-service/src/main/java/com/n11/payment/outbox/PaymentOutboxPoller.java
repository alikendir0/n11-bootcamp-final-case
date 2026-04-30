package com.n11.payment.outbox;

import com.n11.outbox.AbstractOutboxPoller;
import com.n11.outbox.OutboxMessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Payment-service outbox poller (D-09).
 *
 * <p>Extends {@link AbstractOutboxPoller} — concrete subclass provides only the
 * 3-arg constructor injection. All @Scheduled + @Transactional drain logic inherited.
 */
@Component
public class PaymentOutboxPoller extends AbstractOutboxPoller {

    public PaymentOutboxPoller(PaymentOutboxRepository repo,
                               RabbitTemplate rabbitTemplate,
                               OutboxMessagePostProcessor messagePostProcessor) {
        super(repo, rabbitTemplate, messagePostProcessor);
    }
}
