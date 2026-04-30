package com.n11.payment.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Minimal health endpoint for docker-compose healthcheck and smoke testing.
 * Phase 6 adds the real /payments/checkout REST surface.
 */
@RestController("paymentSampleHealthController")
public class SampleHealthController {

    @GetMapping("/sample/health")
    public Map<String, String> health() {
        return Map.of("service", "payment-service", "status", "UP");
    }
}
