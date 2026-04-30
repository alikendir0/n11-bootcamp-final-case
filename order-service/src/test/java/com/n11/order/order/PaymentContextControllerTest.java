package com.n11.order.order;

import com.n11.order.order.dto.PaymentContextDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = PaymentContextController.class, properties = {
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false"
})
class PaymentContextControllerTest {

    @Autowired MockMvc mvc;
    @MockBean PaymentContextService paymentContextService;

    @Test
    void existingOrder_returnsPaymentContextSnapshot() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        PaymentContextDto dto = new PaymentContextDto(
            orderId,
            userId,
            new BigDecimal("199.90"),
            "TRY",
            Instant.parse("2026-04-30T10:15:30Z"),
            new PaymentContextDto.ShippingAddress("Ayşe Yılmaz", "+905551112233", "İstanbul",
                "Kadıköy", "Caferağa", "Moda Caddesi No:1", "34710", "Ev"),
            List.of(new PaymentContextDto.Item(productId, "Kablosuz Kulaklık", 2, new BigDecimal("99.95")))
        );
        when(paymentContextService.getPaymentContext(orderId)).thenReturn(dto);

        mvc.perform(get("/internal/orders/{orderId}/payment-context", orderId))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.orderId").value(orderId.toString()))
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            .andExpect(jsonPath("$.totalAmount").value(199.90))
            .andExpect(jsonPath("$.currency").value("TRY"))
            .andExpect(jsonPath("$.createdAt").value("2026-04-30T10:15:30Z"))
            .andExpect(jsonPath("$.shippingAddress.il").value("İstanbul"))
            .andExpect(jsonPath("$.shippingAddress.ilce").value("Kadıköy"))
            .andExpect(jsonPath("$.items[0].productId").value(productId.toString()))
            .andExpect(jsonPath("$.items[0].nameSnapshot").value("Kablosuz Kulaklık"))
            .andExpect(jsonPath("$.items[0].qty").value(2))
            .andExpect(jsonPath("$.items[0].unitPrice").value(99.95));
    }

    @Test
    void unknownOrder_returnsOpaque404() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(paymentContextService.getPaymentContext(orderId))
            .thenThrow(new ResponseStatusException(NOT_FOUND));

        mvc.perform(get("/internal/orders/{orderId}/payment-context", orderId))
            .andExpect(status().isNotFound());
    }

    @Test
    void paymentContext_isOnlyExposedOnInternalOrdersPath() throws Exception {
        UUID orderId = UUID.randomUUID();

        var result = mvc.perform(get("/orders/{orderId}/payment-context", orderId))
            .andReturn();

        assertThat(result.getHandler()).isInstanceOf(ResourceHttpRequestHandler.class);
    }
}
