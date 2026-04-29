package com.n11.product.health;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class SampleHealthController {

    @Value("${spring.application.name:product-service}")
    private String serviceName;

    @GetMapping("/sample")
    public Map<String, String> sample() {
        return Map.of(
            "service", serviceName,
            "ts", Instant.now().toString(),
            "correlationId", String.valueOf(MDC.get("correlationId"))
        );
    }
}
