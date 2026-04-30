package com.n11.payment.iyzico;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class IyzicoPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TestConfig.class));

    @Test
    void callbackUrlUsesPublicBaseUrlAndGatewayCallbackPath() {
        contextRunner
            .withPropertyValues(
                "iyzico.api-key=test-api-key",
                "iyzico.secret-key=test-secret-key",
                "iyzico.public-base-url=https://demo.example.com")
            .run(context -> assertThat(context.getBean(IyzicoProperties.class).callbackUrl())
                .isEqualTo("https://demo.example.com/api/v1/payments/iyzico/callback"));
    }

    @Test
    void callbackUrlNormalizesTrailingSlashOnPublicBaseUrl() {
        contextRunner
            .withPropertyValues(
                "iyzico.api-key=test-api-key",
                "iyzico.secret-key=test-secret-key",
                "iyzico.public-base-url=https://demo.example.com/")
            .run(context -> assertThat(context.getBean(IyzicoProperties.class).callbackUrl())
                .isEqualTo("https://demo.example.com/api/v1/payments/iyzico/callback"));
    }

    @Test
    void sandboxBaseUrlDefaultsButSecretsHaveNoLiteralDefaults() {
        contextRunner
            .withPropertyValues("iyzico.public-base-url=https://demo.example.com")
            .run(context -> {
                IyzicoProperties properties = context.getBean(IyzicoProperties.class);
                assertThat(properties.baseUrl()).isEqualTo("https://sandbox-api.iyzipay.com");
                assertThat(properties.apiKey()).isNull();
                assertThat(properties.secretKey()).isNull();
            });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IyzicoProperties.class)
    static class TestConfig {
    }
}
