package com.n11.identity.user;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Compile-time placeholder so UserService can be autowired before Plan 03-05
 * ships the real OutboxPublisher. Plan 03-05's implementation is annotated
 * with @Component and (because it is the more specific bean) takes precedence
 * via @ConditionalOnMissingBean. The no-op version below is a SAFETY net.
 *
 * <p>If this no-op runs in production it means Plan 03-05 was not deployed
 * — startup logs will show this class as the active publisher (search MDC
 * for "outbox.publisher.fallback").
 */
@Component
@ConditionalOnMissingBean(name = "outboxBackedUserRegistrationOutboxPublisher")
class NoOpUserRegistrationOutboxPublisher implements UserRegistrationOutboxPublisher {
    @Override
    public void publishRegistered(User user) {
        org.slf4j.LoggerFactory.getLogger(NoOpUserRegistrationOutboxPublisher.class)
                .warn("outbox.publisher.fallback: skipping user.registered for userId={} (Plan 03-05 not deployed)",
                      user.getId());
    }
}
