package com.n11.events;

import com.networknt.schema.AbsoluteIri;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Self-test for T-01-06: confirms the {@link ClasspathOnlySchemaLoader} actually
 * refuses remote URIs. Without this self-test the security claim would be
 * documentation-only (the original BLOCKER finding from RESEARCH §14).
 */
class AbstractEventSchemaTestSecurityCheck {

    @Test
    void rejectsHttpsRef() {
        ClasspathOnlySchemaLoader loader = new ClasspathOnlySchemaLoader();
        assertThatThrownBy(() ->
            loader.getResource(AbsoluteIri.of("https://example.com/evil.schema.json"))
        ).isInstanceOf(RemoteRefRefusedException.class);
    }

    @Test
    void rejectsHttpRef() {
        ClasspathOnlySchemaLoader loader = new ClasspathOnlySchemaLoader();
        assertThatThrownBy(() ->
            loader.getResource(AbsoluteIri.of("http://example.com/evil.schema.json"))
        ).isInstanceOf(RemoteRefRefusedException.class);
    }

    @Test
    void rejectsFileRef() {
        ClasspathOnlySchemaLoader loader = new ClasspathOnlySchemaLoader();
        assertThatThrownBy(() ->
            loader.getResource(AbsoluteIri.of("file:///etc/passwd"))
        ).isInstanceOf(RemoteRefRefusedException.class);
    }
}
