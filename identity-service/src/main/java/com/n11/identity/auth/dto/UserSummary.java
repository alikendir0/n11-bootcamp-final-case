package com.n11.identity.auth.dto;

import java.util.List;
import java.util.UUID;

/** Embedded in AuthResponse + standalone shape returned by /auth/me. */
public record UserSummary(
        UUID id,
        String email,
        String fullName,
        List<String> roles
) { }
