package com.n11.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Spring Cloud Config Server -- centralizes per-service configuration for the n11
 * microservice mesh (ARCH-03 / CD-05). Every business service consumes its config from
 * here at startup via {@code spring.config.import=configserver:http://config-server:8888}
 * (Cross-Cutting #2 -- the Boot 3.x replacement for the deprecated bootstrap context).
 *
 * <p>Backing store: {@code native} profile against {@code classpath:/config/} -- the
 * config repo ships INSIDE this JAR so there is no external git/filesystem mount.
 * The shared baseline lives at {@code src/main/resources/config/application.yml} and
 * every business service inherits it; per-service overrides land in
 * {@code <svc>-service.yml} files added by Plans 01-06 (api-gateway), 01-07
 * (service-template), and Phase 3+ business services.</p>
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
