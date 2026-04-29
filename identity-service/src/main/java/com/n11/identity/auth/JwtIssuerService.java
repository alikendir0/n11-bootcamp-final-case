package com.n11.identity.auth;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mints RS256 JWTs. The encoder bean is wired by {@link JwtConfig}.
 *
 * <p>Claims (D-14, D-05, D-01):
 * <ul>
 *   <li>{@code iss}: "n11-identity" — informational; gateway does NOT validate
 *       issuer in v1 (no issuer-uri config; D-20 uses jwk-set-uri only).</li>
 *   <li>{@code sub}: userId (UUID as String). Gateway extracts -> X-User-Id.</li>
 *   <li>{@code iat}: now</li>
 *   <li>{@code exp}: now + 24h (D-01: long-lived, no refresh endpoint).</li>
 *   <li>{@code roles}: List&lt;String&gt; like ["ROLE_USER"] or
 *       ["ROLE_USER", "ROLE_ADMIN"]. Gateway's
 *       JwtGrantedAuthoritiesConverter (Plan 03-06) reads this claim name.</li>
 *   <li>{@code email}, {@code fullName}: convenience claims so the frontend can
 *       skip an /auth/me round-trip on every page load (D-14 login response).</li>
 * </ul>
 */
@Service
public class JwtIssuerService {

    private static final long TOKEN_LIFETIME_SECONDS = 86400L; // D-01: 24h

    private final JwtEncoder jwtEncoder;

    public JwtIssuerService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    /**
     * @param userId    user UUID (becomes JWT sub claim)
     * @param email     user email (convenience claim)
     * @param fullName  user full name (convenience claim)
     * @param roles     non-null list of role names with ROLE_ prefix (D-05)
     * @return signed JWT compact serialization
     */
    public String issue(UUID userId, String email, String fullName, List<String> roles) {
        Instant now = Instant.now();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("n11-identity")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(TOKEN_LIFETIME_SECONDS))
                .claim("roles", roles)
                .claim("email", email)
                .claim("fullName", fullName)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Token lifetime in seconds — exposed for the login response body's expiresIn field. */
    public long tokenLifetimeSeconds() {
        return TOKEN_LIFETIME_SECONDS;
    }
}
