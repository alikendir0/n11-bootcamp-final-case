package com.n11.ai.interfaces.error;

import com.n11.ai.infrastructure.llm.GeminiUpstreamException;
import com.n11.error.ApiErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * AI-specific error mapping. ProblemDetailControllerAdvice (common-error) covers the
 * standard suite (validation, 404, etc); this advice adds Gemini upstream + tool
 * validation handling per ApiErrorCode entries added in Plan 08-03.
 */
@RestControllerAdvice
public class AiErrorAdvice {

    @ExceptionHandler(GeminiUpstreamException.class)
    public ProblemDetail upstream(GeminiUpstreamException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        pd.setType(ApiErrorCode.UPSTREAM_LLM_ERROR.typeUri());
        pd.setTitle(ApiErrorCode.UPSTREAM_LLM_ERROR.defaultTitle());
        return pd;
    }
}
