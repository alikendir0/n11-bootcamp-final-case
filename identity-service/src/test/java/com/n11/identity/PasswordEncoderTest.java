package com.n11.identity;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-16 / QUAL-02 starter pattern.
 *
 * <p>Pure JUnit, no Spring context, no Testcontainers — fits in &lt; 5 seconds.
 * This is the "smoke unit test on core domain logic" template that every Phase 4+
 * service replicates: one test class per service, three to five direct assertions
 * on a domain-critical behavior, runnable as
 * {@code ./gradlew :&lt;service&gt;:test --tests "*PasswordEncoderTest"}.
 */
class PasswordEncoderTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    @Test
    void encodedPasswordMatchesOriginal() {
        String raw = "TestPassword1";
        String hashed = encoder.encode(raw);
        assertThat(encoder.matches(raw, hashed)).isTrue();
    }

    @Test
    void wrongPasswordDoesNotMatch() {
        String hashed = encoder.encode("TestPassword1");
        assertThat(encoder.matches("WrongPassword2", hashed)).isFalse();
    }

    @Test
    void twoEncodesOfSamePasswordAreDistinct() {
        String raw = "TestPassword1";
        // Salt makes every encode produce a different hash; matches() still works.
        assertThat(encoder.encode(raw)).isNotEqualTo(encoder.encode(raw));
    }

    @Test
    void costFactorIsExactlyTen() {
        String hashed = encoder.encode("any");
        // BCrypt hash format: $2a$<cost>$... — AUTH-07 mandates cost 10.
        assertThat(hashed).startsWith("$2a$10$");
    }
}
