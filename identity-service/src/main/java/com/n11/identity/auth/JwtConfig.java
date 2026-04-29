package com.n11.identity.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;

/**
 * RS256 keypair wiring (D-02). Private key from JWT_PRIVATE_KEY env var (PEM PKCS#8).
 * Public key derived at boot from the private key — eliminates the "two env vars
 * that must match" trap. JWKS endpoint is the only path the gateway uses to learn
 * the public key (D-03).
 *
 * <p>Risk 4 (03-RESEARCH.md): {@code RSAPrivateCrtKey} cast assumes the key was
 * generated in CRT form. Keys generated with {@code openssl genrsa} are always CRT;
 * documented in identity-service/README.md as the only supported generation method.
 */
@Configuration
public class JwtConfig {

    @Bean
    public RSAPrivateKey rsaPrivateKey(@Value("${jwt.private-key}") String pemKey) {
        // docker-compose env_file may pass "\n" as literal characters; restore real newlines.
        String normalized = pemKey.replace("\\n", "\n");
        byte[] keyBytes = normalized.getBytes(StandardCharsets.UTF_8);
        return RsaKeyConverters.pkcs8().convert(new ByteArrayInputStream(keyBytes));
    }

    @Bean
    public RSAPublicKey rsaPublicKey(RSAPrivateKey privateKey)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        // Risk 4: openssl genrsa always emits CRT form, so this cast succeeds.
        // If a non-CRT key is ever supplied, this throws ClassCastException at boot
        // (intentional — fail loud rather than silently mint unverifiable JWTs).
        RSAPrivateCrtKey crt = (RSAPrivateCrtKey) privateKey;
        RSAPublicKeySpec spec = new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent());
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    @Bean
    public RSAKey rsaJwk(RSAPublicKey publicKey,
                         RSAPrivateKey privateKey,
                         @Value("${jwt.key-id}") String keyId) {
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .algorithm(JWSAlgorithm.RS256)
                .keyUse(KeyUse.SIGNATURE)
                .build();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(RSAKey rsaJwk) {
        JWKSet jwkSet = new JWKSet(rsaJwk);
        return (jwkSelector, context) -> jwkSelector.select(jwkSet);
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }
}
