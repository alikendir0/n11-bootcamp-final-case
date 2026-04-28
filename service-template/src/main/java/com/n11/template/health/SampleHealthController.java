package com.n11.template.health;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Smoke endpoint — proves the four D-11 wires work end-to-end:
 *   (a) Springdoc surfaces /sample at /v3/api-docs and /swagger-ui.html
 *   (b) /actuator/health works alongside it
 *   (c) CorrelationIdFilter (from :common-logging) populates MDC, readback via MDC.get("correlationId")
 *   (d) LogstashEncoder includes correlationId in JSON log lines (logback-spring.xml)
 *
 * Verification: curl -H 'X-Correlation-Id: test-cid-1' http://localhost:8080/sample
 *   → JSON body MUST echo correlationId.
 */
@RestController
public class SampleHealthController {

    @Value("${spring.application.name:service-template}")
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
