package com.n11.payment.payment;

import com.n11.payment.iyzico.IyzicoCheckoutClient;
import com.n11.payment.messaging.PaymentTransactionalService;
import com.n11.payment.outbox.PaymentOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link PaymentTimeoutJob} (Plan 06-05 Task 1, PAY-06).
 *
 * <p>Asserts end-to-end timeout sweep behavior against real Testcontainers Postgres:
 * <ul>
 *   <li>An expired PENDING payment becomes {@link PaymentStatus#TIMED_OUT}.</li>
 *   <li>The transition writes EXACTLY ONE {@code payment.failed} outbox row whose
 *       payload contains {@code PAYMENT_TIMEOUT} (errorCode) and {@code TIMEOUT} (reason).</li>
 *   <li>Already-terminal rows (COMPLETED, FAILED, TIMED_OUT) are not picked up by the
 *       scheduler's repository query, so no extra outbox rows are written for them.</li>
 * </ul>
 *
 * <p>RabbitMQ + Iyzico SDK are mocked away (callable surface only — the outbox poller
 * never fires here because we trigger {@code sweepExpiredPendingPayments()} directly).
 *
 * <p>T-06-13 (timeout-vs-callback race): the status guard inside
 * {@link PaymentTransactionalService#timeoutFromScheduler(UUID)} ensures that if a
 * COMPLETED / FAILED row is somehow returned by the query (it shouldn't be, per the
 * @Query filter), the transition is a no-op — the assertion on outbox count proves it.
 */
@SpringBootTest(classes = com.n11.payment.PaymentServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false",
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "mock.payment.delay-ms=0",
        // Disable the @Scheduled trigger so the test drives the sweep manually.
        "payment.timeout.scan-delay-ms=86400000",
        "iyzico.api-key=test-api-key",
        "iyzico.secret-key=test-secret-key",
        "iyzico.public-base-url=http://localhost:8080"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class PaymentTimeoutJobTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("n11").withUsername("postgres").withPassword("postgres");

    @Container
    static GenericContainer<?> RABBIT = new GenericContainer<>(DockerImageName.parse("rabbitmq:3.13-management"))
        .withExposedPorts(5672);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path = payment, public");
        r.add("spring.rabbitmq.host", RABBIT::getHost);
        r.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
        r.add("spring.rabbitmq.username", () -> "guest");
        r.add("spring.rabbitmq.password", () -> "guest");
    }

    @Autowired PaymentTimeoutJob job;
    @Autowired PaymentRepository paymentRepository;
    @Autowired PaymentOutboxRepository outboxRepository;

    @MockBean IyzicoCheckoutClient iyzicoCheckoutClient;
    @MockBean RabbitTemplate rabbitTemplate;

    @BeforeEach
    void cleanState() {
        outboxRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    void expiredPendingPayment_isTimedOut_andEmitsSinglePaymentFailedTimeout() {
        UUID orderId = UUID.randomUUID();
        Payment expired = pending(orderId, "tok-expired", Instant.now().minusSeconds(60));
        paymentRepository.save(expired);

        job.sweepExpiredPendingPayments();

        Payment reloaded = paymentRepository.findById(expired.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.TIMED_OUT);
        assertThat(reloaded.getFailedAt()).isNotNull();

        var allEvents = outboxRepository.findAll();
        // Diagnostic: assert at least one payment.failed row matches the order before
        // narrowing on the schema-aligned reason/errorCode fields.
        var paymentFailedForOrder = allEvents.stream()
            .filter(e -> "payment.failed".equals(e.getEventType()))
            .filter(e -> e.getPayload().contains(orderId.toString()))
            .toList();
        assertThat(paymentFailedForOrder)
            .as("Expected one payment.failed outbox row for order " + orderId
                + "; all outbox rows: " + allEvents)
            .hasSize(1);

        String payload = paymentFailedForOrder.get(0).getPayload();
        // Schema-aligned envelope (see PaymentTransactionalService.publishPaymentFailed):
        //   reason  = "TIMEOUT"           (payment.failed schema enum)
        //   errorCode = "PAYMENT_TIMEOUT"  (granular timeout taxonomy)
        // Note: Postgres JSONB normalizes whitespace ("key": "value") so we substring-match
        // on the values rather than the compact JSON form.
        assertThat(payload).containsPattern("\"reason\"\\s*:\\s*\"TIMEOUT\"");
        assertThat(payload).containsPattern("\"errorCode\"\\s*:\\s*\"PAYMENT_TIMEOUT\"");
    }

    @Test
    void unexpiredPendingPayment_isUntouched() {
        UUID orderId = UUID.randomUUID();
        Payment notYetExpired = pending(orderId, "tok-future", Instant.now().plusSeconds(900));
        paymentRepository.save(notYetExpired);

        job.sweepExpiredPendingPayments();

        Payment reloaded = paymentRepository.findById(notYetExpired.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.PENDING);
        long anyOutbox = outboxRepository.findAll().stream()
            .filter(e -> e.getPayload().contains(orderId.toString()))
            .count();
        assertThat(anyOutbox).isZero();
    }

    @Test
    void terminalRowsAreFilteredByQuery_noTimeoutEvents() {
        UUID completedOrderId = UUID.randomUUID();
        Payment completed = pending(completedOrderId, "tok-completed", Instant.now().minusSeconds(60));
        completed.markCompleted("iyz-pay-99");
        paymentRepository.save(completed);

        UUID failedOrderId = UUID.randomUUID();
        Payment failed = pending(failedOrderId, "tok-failed", Instant.now().minusSeconds(60));
        failed.markFailed("DECLINED", "IYZICO_DECLINED");
        paymentRepository.save(failed);

        UUID timedOutOrderId = UUID.randomUUID();
        Payment timedOut = pending(timedOutOrderId, "tok-already-out", Instant.now().minusSeconds(60));
        timedOut.markTimedOut();
        paymentRepository.save(timedOut);

        job.sweepExpiredPendingPayments();

        // None of the terminal rows should appear in payment.failed outbox emissions
        // triggered by this sweep — the @Query filters by status='PENDING'.
        long timeoutEventsForTerminalRows = outboxRepository.findAll().stream()
            .filter(e -> "payment.failed".equals(e.getEventType()))
            .filter(e -> e.getPayload().contains("PAYMENT_TIMEOUT"))
            .count();
        assertThat(timeoutEventsForTerminalRows).isZero();
    }

    private static Payment pending(UUID orderId, String token, Instant expiresAt) {
        Payment p = new Payment(UUID.randomUUID(), orderId, new BigDecimal("100.00"), "TRY",
            PaymentStatus.PENDING_INITIALIZATION, null);
        p.markPending(token, "https://sandbox.iyzico/pay/" + token, expiresAt);
        return p;
    }
}
