package com.n11.payment.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class PaymentRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("n11").withUsername("postgres").withPassword("postgres");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path = payment, public");
    }

    @Autowired PaymentRepository paymentRepository;

    @Test
    void findsLatestPaymentByOrderIdForActivePendingCheckout() {
        UUID orderId = UUID.randomUUID();
        paymentRepository.save(new Payment(UUID.randomUUID(), orderId, new BigDecimal("10.00"), "TRY",
            PaymentStatus.FAILED, "old-provider-id"));

        Payment pending = new Payment(UUID.randomUUID(), orderId, new BigDecimal("10.00"), "TRY",
            PaymentStatus.PENDING_INITIALIZATION, null);
        pending.markPending("token-123", "https://sandbox.iyzico/payment/token-123", Instant.now().plusSeconds(900));
        paymentRepository.saveAndFlush(pending);

        assertThat(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId))
            .hasValueSatisfying(payment -> {
                assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
                assertThat(payment.getIyzicoToken()).isEqualTo("token-123");
            });
    }

    @Test
    void findsPaymentByUniqueIyzicoTokenForCallbackDeduplication() {
        UUID orderId = UUID.randomUUID();
        Payment pending = new Payment(UUID.randomUUID(), orderId, new BigDecimal("25.50"), "TRY",
            PaymentStatus.PENDING_INITIALIZATION, null);
        pending.markPending("callback-token", "https://sandbox.iyzico/payment/callback-token", Instant.now().plusSeconds(900));
        paymentRepository.saveAndFlush(pending);

        assertThat(paymentRepository.findByIyzicoToken("callback-token"))
            .hasValueSatisfying(payment -> assertThat(payment.getOrderId()).isEqualTo(orderId));
    }

    @Test
    void statusEnumContainsCheckoutLifecycleStates() {
        assertThat(PaymentStatus.values()).contains(
            PaymentStatus.PENDING_INITIALIZATION,
            PaymentStatus.PENDING,
            PaymentStatus.COMPLETED,
            PaymentStatus.FAILED,
            PaymentStatus.TIMED_OUT
        );
    }
}
