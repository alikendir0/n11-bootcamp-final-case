package com.n11.identity.auth.dto;

/**
 * D-14 response shape — used by both /auth/register and /auth/login.
 *
 * <pre>
 * { "accessToken": "...", "tokenType": "Bearer", "expiresIn": 86400,
 *   "user": { "id": "...", "email": "...", "fullName": "...", "roles": [...] } }
 * </pre>
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserSummary user
) {
    public static AuthResponse bearer(String accessToken, long expiresIn, UserSummary user) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, user);
    }
}
