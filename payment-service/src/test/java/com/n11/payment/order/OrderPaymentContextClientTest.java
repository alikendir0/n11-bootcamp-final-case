package com.n11.payment.order;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class OrderPaymentContextClientTest {

    @Test
    void getPaymentContext_callsInternalOrderEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID orderId = UUID.randomUUID();
        server.expect(once(), requestTo("http://orders.local/internal/orders/" + orderId + "/payment-context"))
            .andExpect(method(GET))
            .andRespond(withSuccess("""
                {
                  "orderId": "%s",
                  "userId": "%s",
                  "totalAmount": 199.90,
                  "currency": "TRY",
                  "createdAt": "2026-04-30T10:15:30Z",
                  "shippingAddress": {
                    "recipientName": "Ayşe Yılmaz",
                    "phone": "+905551112233",
                    "il": "İstanbul",
                    "ilce": "Kadıköy",
                    "mahalle": "Caferağa",
                    "streetLine": "Moda Caddesi No:1",
                    "postalCode": "34710",
                    "title": "Ev"
                  },
                  "items": [{
                    "productId": "%s",
                    "nameSnapshot": "Kablosuz Kulaklık",
                    "qty": 2,
                    "unitPrice": 99.95
                  }]
                }
                """.formatted(orderId, UUID.randomUUID(), UUID.randomUUID()), MediaType.APPLICATION_JSON));
        OrderPaymentContextClient client = new OrderPaymentContextClient(builder, "http://orders.local");

        OrderPaymentContext context = client.getPaymentContext(orderId);

        assertThat(context.orderId()).isEqualTo(orderId);
        assertThat(context.currency()).isEqualTo("TRY");
        assertThat(context.createdAt()).isEqualTo(java.time.Instant.parse("2026-04-30T10:15:30Z"));
        assertThat(context.shippingAddress().il()).isEqualTo("İstanbul");
        assertThat(context.items()).hasSize(1);
        server.verify();
    }

    @Test
    void getPaymentContext_translates404ToPaymentInitializationFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID orderId = UUID.randomUUID();
        server.expect(once(), requestTo("http://orders.local/internal/orders/" + orderId + "/payment-context"))
            .andExpect(method(GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));
        OrderPaymentContextClient client = new OrderPaymentContextClient(builder, "http://orders.local");

        assertThatThrownBy(() -> client.getPaymentContext(orderId))
            .isInstanceOf(PaymentInitializationException.class)
            .hasMessageContaining("payment context unavailable");
        server.verify();
    }
}
