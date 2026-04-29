package com.n11.events;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
// [Rule 1 - Bug] StatefulRetryOperationsInterceptor lives in spring-retry, not spring-rabbit
// (Spring AMQP 3.2.x removed the spring-rabbit shadow class — only RetryInterceptorBuilder
// remains in org.springframework.amqp.rabbit.config). Plan 01-04 import path was stale.
import org.springframework.retry.interceptor.StatefulRetryOperationsInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Saga retry policy — D-09 / Cross-Cutting #8 / .planning/saga-contracts.md §4.
 *
 * <p>Wording lock (do not edit — surface as-is in SUMMARY.md):
 * "3 total attempts (= 1 initial + 2 retries). Delays between attempts: 1s, then 5s. After the 3rd attempt fails, the message goes to DLQ. The 30s upper bound is a safety cap on the exponential growth of the backoff (multiplier=5, max=30000ms), not a delay between attempts 3 and 4 — there is no attempt 4."
 *
 * <p>Phase 1 ships the <em>config beans</em> only; no actual queues are declared (Phase 5+
 * services declare their own Declarables beans).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RetryInterceptorBuilder.class)
public class RabbitRetryConfig {

    /**
     * Stateful retry interceptor: attempts 3, delays [1s, 5s], cap 30s.
     * After exhaustion, RejectAndDontRequeueRecoverer routes the message
     * to the queue's DLX (configured per-queue via x-dead-letter-exchange).
     */
    @Bean
    public StatefulRetryOperationsInterceptor sagaRetryInterceptor() {
        return RetryInterceptorBuilder.stateful()
            .maxAttempts(3)
            .backOffOptions(1000L, 5.0, 30000L)
            .recoverer(new RejectAndDontRequeueRecoverer())
            .messageKeyGenerator(message -> {
                String mid = message.getMessageProperties().getMessageId();
                if (mid == null || mid.isBlank()) {
                    throw new AmqpException(
                        "Stateful retry requires a non-null messageId — producer must set "
                        + "MessageProperties.setMessageId(eventId) before publish");
                }
                return mid;
            })
            .build();
    }

    /**
     * Listener container factory wired with AUTO ack + the retry interceptor advice chain.
     *
     * <p>Ack-mode reasoning: {@link AcknowledgeMode#AUTO} is correct here because
     * {@link RejectAndDontRequeueRecoverer} does <em>not</em> call {@code channel.basicNack}
     * directly — it throws {@code ListenerExecutionFailedException(AmqpRejectAndDontRequeueException)},
     * which the container's {@code ConditionalRejectingErrorHandler} maps to a no-requeue nack.
     * AUTO mode means:
     * <ul>
     *   <li>Listener returns normally → message auto-acked.</li>
     *   <li>Listener throws (interceptor exhausted, recoverer throws) → container nacks with
     *       requeue=false because {@code AmqpRejectAndDontRequeueException} is "fatal".</li>
     * </ul>
     * Using {@code MANUAL} mode without {@code channel.basicAck()} in the listener leaves
     * every successfully-processed message perpetually unacked, causing infinite redelivery
     * on the next consumer cycle or connection reset.
     *
     * <p>Phase 5+ consumers reference this bean by name.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory cf,
            StatefulRetryOperationsInterceptor sagaRetryInterceptor) {
        SimpleRabbitListenerContainerFactory f = new SimpleRabbitListenerContainerFactory();
        f.setConnectionFactory(cf);
        f.setAcknowledgeMode(AcknowledgeMode.AUTO);
        f.setAdviceChain(sagaRetryInterceptor);
        return f;
    }
}
