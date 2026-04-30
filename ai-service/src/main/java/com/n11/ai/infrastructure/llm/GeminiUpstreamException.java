package com.n11.ai.infrastructure.llm;

/**
 * Thrown when the Gemini upstream API call fails.
 * Translated to ApiErrorCode.UPSTREAM_LLM_ERROR by the exception handler (Plan 04).
 */
public class GeminiUpstreamException extends RuntimeException {

    public GeminiUpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
