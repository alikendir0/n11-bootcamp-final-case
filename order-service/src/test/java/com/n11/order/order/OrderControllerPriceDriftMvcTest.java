package com.n11.order.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.order.order.dto.CreateOrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(OrderExceptionHandler.class)
class OrderControllerPriceDriftMvcTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean OrderService orderService;
    @MockBean OrderQueryService orderQueryService;
    @MockBean OrderCancellationService orderCancellationService;

    @Test
    void priceDrift_returns409_withRfc7807UpdatedItems() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();

        OrderService.PriceDriftException ex = new OrderService.PriceDriftException(
            List.of(new OrderService.UpdatedItem(productId, new BigDecimal("80.00"), new BigDecimal("100.00"))));
        when(orderService.createOrder(any(), any(), any())).thenThrow(ex);

        String body = objectMapper.writeValueAsString(new CreateOrderRequest(addressId, "CARD"));

        mvc.perform(post("/orders")
                .header("X-User-Id", userId.toString())
                .header("Idempotency-Key", idempotencyKey.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().is(409))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://n11clone/errors/price-drift"))
            .andExpect(jsonPath("$.title").value("Price drift detected"))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.updatedItems").isArray())
            .andExpect(jsonPath("$.updatedItems[0].productId").value(productId.toString()))
            .andExpect(jsonPath("$.updatedItems[0].oldUnitPrice").value(100.00))
            .andExpect(jsonPath("$.updatedItems[0].newUnitPrice").value(80.00));
    }
}
