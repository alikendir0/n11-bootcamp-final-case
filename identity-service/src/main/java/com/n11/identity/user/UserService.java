package com.n11.identity.user;

import com.n11.identity.auth.JwtIssuerService;
import com.n11.identity.auth.dto.AuthResponse;
import com.n11.identity.auth.dto.LoginRequest;
import com.n11.identity.auth.dto.RegisterRequest;
import com.n11.identity.auth.dto.UserProfileResponse;
import com.n11.identity.auth.dto.UserSummary;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates registration, login, and profile lookup.
 *
 * <p>{@code register} writes {@code users} + {@code user_roles} rows AND a
 * {@code user.registered} outbox row in a single {@code @Transactional}
 * boundary (D-12). Plan 03-05 fills in the actual outbox-row save; this
 * plan ships the seam (the {@code OutboxPublisher} placeholder injection
 * is a deliberate forward reference — keep the @Autowired field optional
 * via {@code @Autowired(required = false)} so this class compiles before
 * the publisher is wired).
 *
 * <p>D-07: register always assigns {@code ROLE_USER} only.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuerService jwtIssuerService;
    private final UserRegistrationOutboxPublisher outboxPublisher; // wired in Plan 03-05

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       JwtIssuerService jwtIssuerService,
                       UserRegistrationOutboxPublisher outboxPublisher) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuerService = jwtIssuerService;
        this.outboxPublisher = outboxPublisher;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-posta adresi zaten kayıtlı");
        }

        Role userRole = roleRepository.findByName(Role.NAME_USER)
                .orElseThrow(() -> new IllegalStateException("ROLE_USER seed missing"));

        User user = new User(
                UUID.randomUUID(),
                req.email(),
                passwordEncoder.encode(req.password()),
                req.fullName(),
                Instant.now()
        );
        user.addRole(userRole);
        User saved = userRepository.save(user);

        // D-12: same-TX outbox row for user.registered. Plan 03-05 implements
        // UserRegistrationOutboxPublisher.publishRegistered(...).
        outboxPublisher.publishRegistered(saved);

        return buildAuthResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "E-posta veya şifre hatalı"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-posta veya şifre hatalı");
        }
        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı"));
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                roleNames(user),
                user.getCreatedAt()
        );
    }

    private AuthResponse buildAuthResponse(User user) {
        List<String> roles = roleNames(user);
        String token = jwtIssuerService.issue(user.getId(), user.getEmail(), user.getFullName(), roles);
        UserSummary summary = new UserSummary(user.getId(), user.getEmail(), user.getFullName(), roles);
        return AuthResponse.bearer(token, jwtIssuerService.tokenLifetimeSeconds(), summary);
    }

    private static List<String> roleNames(User user) {
        return user.getRoles().stream()
                .map(Role::getName)
                .sorted()
                .collect(Collectors.toList());
    }
}
