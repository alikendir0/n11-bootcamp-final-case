package com.n11.identity.auth;

import com.n11.identity.auth.dto.AuthResponse;
import com.n11.identity.auth.dto.LoginRequest;
import com.n11.identity.auth.dto.RegisterRequest;
import com.n11.identity.auth.dto.UserProfileResponse;
import com.n11.identity.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Auth endpoints per .planning/api-contracts.md §1.
 *
 * <p>D-15: identity-service does not validate JWTs. {@link #me(HttpServletRequest)}
 * reads the gateway-injected {@code X-User-Id} header. The gateway is the only
 * JWT verifier; identity-service only signs.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String HEADER_USER_ID = "X-User-Id";

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest body) {
        return userService.register(body);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest body) {
        return userService.login(body);
    }

    @GetMapping("/me")
    public UserProfileResponse me(HttpServletRequest request) {
        // D-15: read the gateway-injected header — zero JWT decoding here.
        String userId = request.getHeader(HEADER_USER_ID);
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Kimlik doğrulaması gerekli");
        }
        UUID id;
        try {
            id = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz kullanıcı kimliği");
        }
        return userService.getProfile(id);
    }
}
