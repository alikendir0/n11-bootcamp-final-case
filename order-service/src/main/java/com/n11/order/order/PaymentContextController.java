package com.n11.order.order;

import com.n11.order.order.dto.PaymentContextDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController("orderPaymentContextController")
@RequestMapping("/internal/orders")
public class PaymentContextController {

    private final PaymentContextService paymentContextService;

    public PaymentContextController(PaymentContextService paymentContextService) {
        this.paymentContextService = paymentContextService;
    }

    @GetMapping("/{orderId}/payment-context")
    public PaymentContextDto getPaymentContext(@PathVariable UUID orderId) {
        return paymentContextService.getPaymentContext(orderId);
    }
}
