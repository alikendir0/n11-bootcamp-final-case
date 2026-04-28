package com.n11.logging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Wire #3B of 5: outbound AMQP correlation-ID propagation (template-mutator half).
 *
 * <b>Bean wiring strategy (BLOCKER fix):</b> Spring Boot's RabbitAutoConfiguration already
 * provides a primary RabbitTemplate bean. We do NOT register a second one (that would
 * cause NoUniqueBeanDefinitionException). Instead, this @Configuration registers a
 * BeanPostProcessor that finds the auto-configured RabbitTemplate post-creation and
 * attaches CorrelationIdMessagePostProcessor via addBeforePublishPostProcessors.
 *
 * <p>@Import pulls the helper @Components in explicitly so the auto-config is fully
 * self-contained (consumers do NOT need @ComponentScan over com.n11.logging).
 *
 * <p>@ConditionalOnClass(RabbitTemplate.class) lets non-AMQP services depend on
 * common-logging without forcing them to pull spring-rabbit.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RabbitTemplate.class)
@Import({ CorrelationIdMessagePostProcessor.class, RabbitListenerCorrelationAspect.class })
public class RabbitTemplateConfig {

    @Bean
    public BeanPostProcessor correlationIdRabbitTemplatePostProcessor(
            CorrelationIdMessagePostProcessor cidpp) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof RabbitTemplate template) {
                    template.addBeforePublishPostProcessors(cidpp);
                }
                return bean;
            }
        };
    }
}
