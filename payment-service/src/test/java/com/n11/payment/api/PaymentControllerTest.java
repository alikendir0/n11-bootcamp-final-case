package com.n11.payment.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.payment.iyzico.IyzicoCheckoutClient;
import com.n11.payment.messaging.PaymentTransactionalService;
import com.n11.payment.payment.Payment;
import com.n11.payment.payment.PaymentRepository;
import com.n11.payment.payment.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice tests for {@link PaymentController}: payment status/link API surface (D-06/D-08).
 *
 * <p>Asserts:
 * <ul>
 *   <li>GET /payments/{orderId} returns 202 PENDING_INITIALIZATION when no row exists.</li>
 *   <li>GET /payments/{orderId} returns 200 with paymentPageUrl for PENDING checkout.</li>
 *   <li>POST /payments/checkout is an idempotent ensure/fetch — returns the SAME status
 *       payload as GET; never creates a second checkout.</li>
 *   <li>The response shape never includes a {@code checkoutFormContent} field (D-07).</li>
 * </ul>
 */
@WebMvcTest(controllers = PaymentController.class)
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false"
})
class PaymentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean PaymentRepository paymentRepository;
    @MockBean IyzicoCheckoutClient iyzicoCheckoutClient;
    @MockBean PaymentTransactionalService paymentTransactionalService;

    @Test
    void getPayment_returns202_whenNoPaymentRowYet() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/payments/{orderId}", orderId))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.orderId", is(orderId.toString())))
            .andExpect(jsonPath("$.status", is("PENDING_INITIALIZATION")))
            .andExpect(jsonPath("$.paymentPageUrl").value(nullValue()))
            .andExpect(jsonPath("$.checkoutFormContent").doesNotExist());
    }

    @Test
    void getPayment_returns200_withPaymentPageUrl_forPendingCheckout() throws Exception {
        UUID orderId = UUID.randomUUID();
        Payment payment = pendingPayment(orderId);
        when(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.of(payment));

        mockMvc.perform(get("/payments/{orderId}", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId", is(orderId.toString())))
            .andExpect(jsonPath("$.status", is("PENDING")))
            .andExpect(jsonPath("$.paymentPageUrl", is("https://sandbox.iyzico/pay/token-x")))
            .andExpect(jsonPath("$.failureReason").value(nullValue()))
            .andExpect(jsonPath("$.updatedAt", notNullValue()))
            .andExpect(jsonPath("$.checkoutFormContent").doesNotExist());
    }

    @Test
    void postCheckout_returnsSameStatusPayload_asGet_andDoesNotCreateSecondCheckout() throws Exception {
        UUID orderId = UUID.randomUUID();
        Payment payment = pendingPayment(orderId);
        when(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.of(payment));
        // Guarantee the controller did not save anything new.
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            throw new AssertionError("POST /payments/checkout must not create a new payment row");
        });

        String body = "{\"orderId\":\"" + orderId + "\"}";

        mockMvc.perform(post("/payments/checkout")
                .contentType("application/json")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId", is(orderId.toString())))
            .andExpect(jsonPath("$.status", is("PENDING")))
            .andExpect(jsonPath("$.paymentPageUrl", is("https://sandbox.iyzico/pay/token-x")))
            .andExpect(jsonPath("$.checkoutFormContent").doesNotExist());
    }

    @Test
    void postCheckout_returns202_whenNoPaymentYet() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.empty());

        String body = "{\"orderId\":\"" + orderId + "\"}";

        mockMvc.perform(post("/payments/checkout")
                .contentType("application/json")
                .content(body))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status", is("PENDING_INITIALIZATION")))
            .andExpect(jsonPath("$.paymentPageUrl").value(nullValue()));
    }

    @Test
    void getPayment_returnsCompleted_withoutConsumedPaymentPageUrl() throws Exception {
        UUID orderId = UUID.randomUUID();
        Payment payment = pendingPayment(orderId);
        payment.markCompleted("iyzico-payment-id");
        when(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.of(payment));

        mockMvc.perform(get("/payments/{orderId}", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("COMPLETED")))
            .andExpect(jsonPath("$.paymentPageUrl").value(nullValue()));
    }

    @Test
    void getIyzicoCallback_reachesController_andReturns404ForUnknownToken() throws Exception {
        when(paymentRepository.findByIyzicoToken("unknown-token")).thenReturn(Optional.empty());

        mockMvc.perform(get("/payments/iyzico/callback")
                .queryParam("token", "unknown-token"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void getIyzicoCallback_returns400Html_whenTokenMissing() throws Exception {
        mockMvc.perform(get("/payments/iyzico/callback"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    private static Payment pendingPayment(UUID orderId) {
        Payment payment = new Payment(UUID.randomUUID(), orderId, new BigDecimal("250.50"), "TRY",
            PaymentStatus.PENDING_INITIALIZATION, null);
        payment.markPending("token-x", "https://sandbox.iyzico/pay/token-x", Instant.now().plusSeconds(900));
        return payment;
    }

    @SuppressWarnings("unused")
    private static List<Payment> noPayments() { return List.of(); }
}
