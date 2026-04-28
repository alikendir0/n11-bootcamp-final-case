package com.n11.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

/**
 * Wire #4 of 5: inbound AMQP correlation-ID propagation (aspect skeleton).
 *
 * <b>PHASE 1 STATUS: SKELETON ONLY.</b> Activated in Phase 5+ when @RabbitListener
 * consumers are added. The pointcut is `@annotation(...RabbitListener)` so the
 * aspect only fires on annotated consumer methods.
 *
 * <p>The activation logic (Phase 5+):
 * <ol>
 *   <li>Iterate join-point args; the AMQP Message argument carries headers.</li>
 *   <li>Read X-Correlation-Id from message headers (or AMQP `correlation_id` property).</li>
 *   <li>MDC.put("correlationId", value); proceed; MDC.remove in finally.</li>
 * </ol>
 *
 * <p>For Phase 1 the body is a transparent passthrough so the bean wires up
 * without breaking; the test infrastructure can assert the bean exists.
 */
@Aspect
@Component
public class RabbitListenerCorrelationAspect {

    public static final String CORRELATION_HEADER = CorrelationIdFilter.HEADER;
    public static final String MDC_KEY            = CorrelationIdFilter.MDC_KEY;

    @Around("@annotation(org.springframework.amqp.rabbit.annotation.RabbitListener)")
    public Object propagateCorrelationId(ProceedingJoinPoint pjp) throws Throwable {
        String cid = extractCorrelationId(pjp);
        boolean placed = false;
        try {
            if (cid != null && !cid.isBlank()) {
                MDC.put(MDC_KEY, cid);
                placed = true;
            }
            return pjp.proceed();
        } finally {
            if (placed) {
                MDC.remove(MDC_KEY);
            }
        }
    }

    /**
     * Extract correlation-id from the first AMQP Message argument seen on the
     * join point. Phase 5+ will exercise this; Phase 1 has no @RabbitListener
     * so this method is reachable only in tests.
     */
    private String extractCorrelationId(ProceedingJoinPoint pjp) {
        for (Object arg : pjp.getArgs()) {
            if (arg instanceof Message msg) {
                String header = (String) msg.getMessageProperties().getHeaders().get(CORRELATION_HEADER);
                if (header != null && !header.isBlank()) return header;
                String prop = msg.getMessageProperties().getCorrelationId();
                if (prop != null && !prop.isBlank()) return prop;
            }
        }
        return null;
    }
}
