package com.n11.cart.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SampleHealthController {
    @GetMapping("/sample/health")
    public Map<String, String> health() { return Map.of("service", "cart-service", "status", "UP"); }
}
