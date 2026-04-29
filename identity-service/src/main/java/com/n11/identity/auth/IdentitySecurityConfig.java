package com.n11.identity.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Servlet-side Security config (D-15): identity-service does NOT validate JWTs.
 * The gateway is the single JWT verification chokepoint. Identity-service's
 * SecurityFilterChain is permit-all to suppress Spring Security's default
 * form-login (which would otherwise fire because spring-boot-starter-security
 * is on the classpath). All endpoint security is the gateway's job.
 *
 * <p>BCryptPasswordEncoder bean (cost 10 per AUTH-07 / D-16) lives here so it
 * is available to UserService (Plan 03-04) and PasswordEncoderTest (Task 2).
 */
@Configuration
@EnableWebSecurity
public class IdentitySecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(ex -> ex.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // AUTH-07: BCrypt cost factor 10. PasswordEncoderTest asserts this exact prefix.
        return new BCryptPasswordEncoder(10);
    }
}
