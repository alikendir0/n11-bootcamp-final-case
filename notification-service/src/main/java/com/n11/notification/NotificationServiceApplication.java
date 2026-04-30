package com.n11.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

// @EntityScan("com.n11") is implicit via scanBasePackages="com.n11" (Pitfall 6 mitigation).
// All local @Entity classes in com.n11.notification.* are discovered automatically.
@SpringBootApplication(scanBasePackages = "com.n11")
@EnableDiscoveryClient
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
