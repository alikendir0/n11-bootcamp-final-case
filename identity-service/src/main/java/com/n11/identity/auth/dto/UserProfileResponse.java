package com.n11.identity.auth.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response body for GET /auth/me. Distinct from {@link UserSummary} only
 * because we surface createdAt here for the account-page header.
 */
public record UserProfileResponse(
        UUID id,
        String email,
        String fullName,
        List<String> roles,
        Instant createdAt
) { }
