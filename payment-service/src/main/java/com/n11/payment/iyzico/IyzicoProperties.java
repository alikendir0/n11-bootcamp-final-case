package com.n11.payment.iyzico;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Environment-backed Iyzico Checkout Form settings.
 *
 * <p>Secrets intentionally have no literal defaults. Production values come from Spring Cloud
 * Config placeholders bound to environment variables.</p>
 */
@ConfigurationProperties(prefix = "iyzico")
public record IyzicoProperties(
    String baseUrl,
    String apiKey,
    String secretKey,
    String publicBaseUrl,
    long timeoutMinutes,
    String demoBuyerIdentityNumber
) {

    private static final String DEFAULT_SANDBOX_BASE_URL = "https://sandbox-api.iyzipay.com";
    private static final long DEFAULT_TIMEOUT_MINUTES = 15L;
    private static final String DEFAULT_DEMO_BUYER_IDENTITY_NUMBER = "74300864791";
    private static final String CALLBACK_PATH = "/api/v1/payments/iyzico/callback";

    public IyzicoProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_SANDBOX_BASE_URL;
        }
        if (timeoutMinutes <= 0) {
            timeoutMinutes = DEFAULT_TIMEOUT_MINUTES;
        }
        if (demoBuyerIdentityNumber == null || demoBuyerIdentityNumber.isBlank()) {
            demoBuyerIdentityNumber = DEFAULT_DEMO_BUYER_IDENTITY_NUMBER;
        }
    }

    public String callbackUrl() {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw new IllegalStateException("iyzico.public-base-url must be configured from PUBLIC_BASE_URL");
        }
        return publicBaseUrl.replaceAll("/+$", "") + CALLBACK_PATH;
    }
}
