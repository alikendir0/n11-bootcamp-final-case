package com.n11.payment.api;

import com.n11.payment.iyzico.IyzicoCheckoutClient;
import com.n11.payment.iyzico.IyzicoCheckoutResult;
import com.n11.payment.payment.Payment;
import com.n11.payment.payment.PaymentRepository;
import com.n11.payment.payment.PaymentStatus;
import com.n11.payment.outbox.PaymentOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Iyzico callback integration test (Plan 06-04 Task 2).
 *
 * <p>Asserts saga-finalization behavior of {@code POST /payments/iyzico/callback}:
 * <ul>
 *   <li>Successful retrieve transitions PENDING → COMPLETED and writes EXACTLY ONE
 *       {@code payment.completed} outbox row.</li>
 *   <li>Failed retrieve transitions PENDING → FAILED and writes EXACTLY ONE
 *       {@code payment.failed} outbox row.</li>
 *   <li>Duplicate callback for the same token is idempotent — no extra outbox row.</li>
 * </ul>
 *
 * <p>Uses Testcontainers Postgres (real Flyway migrations); the {@link IyzicoCheckoutClient}
 * is mocked to return controlled retrieve responses, and {@link RabbitTemplate} is mocked
 * to avoid AMQP startup.
 */
@SpringBootTest(classes = com.n11.payment.PaymentServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false",
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "mock.payment.delay-ms=0",
        "iyzico.api-key=test-api-key",
        "iyzico.secret-key=test-secret-key",
        "iyzico.public-base-url=http://localhost:8080"
    })
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class IyzicoCallbackIntegrationTest {

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

    @Autowired MockMvc mockMvc;
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
    void successfulRetrieve_writesPaymentCompleted_exactlyOnce() throws Exception {
        UUID orderId = UUID.randomUUID();
        Payment pending = pending(orderId, "tok-success");
        paymentRepository.save(pending);
        when(iyzicoCheckoutClient.retrieve(any(), any())).thenReturn(
            new IyzicoCheckoutResult.RetrievedCheckout(
                "tok-success", "iyz-pay-1", "SUCCESS", 1, null, null));

        mockMvc.perform(post("/payments/iyzico/callback")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("token", "tok-success"))
            .andExpect(status().isOk());

        var stored = paymentRepository.findByIyzicoToken("tok-success").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(stored.getIyzicoPaymentId()).isEqualTo("iyz-pay-1");

        long completed = outboxRepository.findAll().stream()
            .filter(e -> "payment.completed".equals(e.getEventType()))
            .filter(e -> e.getPayload().contains(orderId.toString()))
            .count();
        assertThat(completed).isEqualTo(1L);
    }

    @Test
    void failedRetrieve_writesPaymentFailed_exactlyOnce() throws Exception {
        UUID orderId = UUID.randomUUID();
        paymentRepository.save(pending(orderId, "tok-fail"));
        when(iyzicoCheckoutClient.retrieve(any(), any())).thenReturn(
            new IyzicoCheckoutResult.RetrievedCheckout(
                "tok-fail", null, "FAILURE", 1, "10051", "İşlem reddedildi"));

        mockMvc.perform(post("/payments/iyzico/callback")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("token", "tok-fail"))
            .andExpect(status().isOk());

        var stored = paymentRepository.findByIyzicoToken("tok-fail").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(stored.getFailureCode()).isEqualTo("IYZICO_DECLINED");

        long failed = outboxRepository.findAll().stream()
            .filter(e -> "payment.failed".equals(e.getEventType()))
            .filter(e -> e.getPayload().contains(orderId.toString()))
            .count();
        assertThat(failed).isEqualTo(1L);
    }

    @Test
    void duplicateCallback_isIdempotent_noSecondOutbox() throws Exception {
        UUID orderId = UUID.randomUUID();
        paymentRepository.save(pending(orderId, "tok-dup"));
        when(iyzicoCheckoutClient.retrieve(any(), any())).thenReturn(
            new IyzicoCheckoutResult.RetrievedCheckout(
                "tok-dup", "iyz-pay-2", "SUCCESS", 1, null, null));

        mockMvc.perform(post("/payments/iyzico/callback")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("token", "tok-dup"))
            .andExpect(status().isOk());

        // Second delivery for same token (e.g. user double-submits hosted form, or Iyzico replays)
        mockMvc.perform(post("/payments/iyzico/callback")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("token", "tok-dup"))
            .andExpect(status().isOk());

        var stored = paymentRepository.findByIyzicoToken("tok-dup").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        long completed = outboxRepository.findAll().stream()
            .filter(e -> "payment.completed".equals(e.getEventType()))
            .filter(e -> e.getPayload().contains(orderId.toString()))
            .count();
        assertThat(completed).isEqualTo(1L);
        // Duplicate terminal callbacks short-circuit: payment is already COMPLETED,
        // so retrieve is called exactly once (the second delivery returns the cached
        // terminal status without re-hitting Iyzico). T-06-10 mitigation.
        verify(iyzicoCheckoutClient, times(1)).retrieve(any(), any());
    }

    @Test
    void retrieveResponseMdStatusInvalid_writesPaymentFailedWith3dsReason() throws Exception {
        UUID orderId = UUID.randomUUID();
        paymentRepository.save(pending(orderId, "tok-md"));
        // mdStatus-invalid retrieve: response status SUCCESS but paymentStatus not SUCCESS, mdStatus=0/4
        when(iyzicoCheckoutClient.retrieve(any(), any())).thenReturn(
            new IyzicoCheckoutResult.RetrievedCheckout(
                "tok-md", null, "MD_STATUS_INVALID", 1, "10005", "3DS doğrulama başarısız"));

        mockMvc.perform(post("/payments/iyzico/callback")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("token", "tok-md"))
            .andExpect(status().isOk());

        var stored = paymentRepository.findByIyzicoToken("tok-md").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(stored.getFailureCode()).isEqualTo("IYZICO_3DS_MDSTATUS_INVALID");

        long failed = outboxRepository.findAll().stream()
            .filter(e -> "payment.failed".equals(e.getEventType()))
            .filter(e -> e.getPayload().contains(orderId.toString()))
            .filter(e -> e.getPayload().contains("IYZICO_3DS_MDSTATUS_INVALID"))
            .count();
        assertThat(failed).isEqualTo(1L);
    }

    private static Payment pending(UUID orderId, String token) {
        Payment p = new Payment(UUID.randomUUID(), orderId, new BigDecimal("250.50"), "TRY",
            PaymentStatus.PENDING_INITIALIZATION, null);
        p.markPending(token, "https://sandbox.iyzico/pay/" + token, Instant.now().plusSeconds(900));
        return p;
    }
}
