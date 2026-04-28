package com.n11.error;

import java.net.URI;

/**
 * Locked RFC-7807 error code namespace per D-09 and .planning/api-contracts.md §7.
 *
 * Each enum value carries its `type` URI suffix; the full URI is
 * https://n11clone/errors/{suffix}.
 *
 * The 5 entries are exhaustive — every error response across all services
 * MUST map to exactly one of these values.
 */
public enum ApiErrorCode {
    VALIDATION("validation",   "Validation failed"),
    NOT_FOUND("not-found",     "Resource not found"),
    CONFLICT("conflict",       "State conflict"),
    UNAUTHORIZED("unauthorized","Unauthorized"),
    INTERNAL("internal",       "Internal server error");

    private static final String TYPE_BASE = "https://n11clone/errors/";

    private final String suffix;
    private final String defaultTitle;

    ApiErrorCode(String suffix, String defaultTitle) {
        this.suffix = suffix;
        this.defaultTitle = defaultTitle;
    }

    public URI typeUri() {
        return URI.create(TYPE_BASE + suffix);
    }

    public String defaultTitle() {
        return defaultTitle;
    }
}
