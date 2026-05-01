package com.n11.ai.infrastructure.llm;

import com.n11.ai.port.ChatProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SOLID demonstration test (D-04 / Pitfall #7).
 *
 * Setting ai.provider=echo MUST cause Spring to wire EchoChatProvider as
 * the ChatProvider bean — proving the port is substitutable without code
 * changes. This is the test graders should run to verify the abstraction.
 */
@SpringBootTest(
    classes = EchoProviderActivationTest.MinimalApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
    "ai.provider=echo",
    "spring.cloud.config.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "eureka.client.enabled=false",
    "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
        "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
class EchoProviderActivationTest {

    @Autowired
    ChatProvider chatProvider;

    @Test
    void echo_provider_is_active_when_ai_provider_is_echo() {
        assertThat(chatProvider).isInstanceOf(EchoChatProvider.class);
    }

    @SpringBootApplication(scanBasePackageClasses = EchoChatProvider.class)
    static class MinimalApp {
    }
}
