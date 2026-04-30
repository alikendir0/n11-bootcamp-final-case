package com.n11.order.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController("orderSampleHealthController")
public class SampleHealthController {
    @GetMapping("/sample/health")
    public Map<String, String> health() { return Map.of("service", "order-service", "status", "UP"); }
}
