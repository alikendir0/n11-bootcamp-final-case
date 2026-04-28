package com.n11.events;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.resource.ResourceLoader;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Saga drift gate (D-08): every Phase 5+ saga producer integration test
 * extends this base and calls {@code assertEventValid(eventType, json)} on a
 * freshly produced event JSON. The test fails if the producer's output drifts
 * from the canonical schema in {@code /saga-schemas/{eventType}.schema.json}.
 *
 * <p><b>networknt 3.0.2 API note (Rule-1 deviation from PLAN code):</b>
 * Plan 01-04 was drafted against networknt 2.x classnames
 * ({@code JsonSchemaFactory}, {@code SpecVersion}, {@code SchemaValidatorsConfig},
 * {@code ValidationMessage}). The 3.0.2 release renamed those to {@link SchemaRegistry},
 * {@link SpecificationVersion}, {@code SchemaRegistryConfig}, and {@link Error},
 * and migrated to Jackson 3.x ({@code tools.jackson.databind.JsonNode}). Phase 1
 * locked 3.0.2 in the version catalog; rather than downgrade, this base class
 * uses {@link Schema#validate(String, InputFormat)} which takes a raw JSON String
 * — sidestepping the Jackson 2.x (Envelope record) vs Jackson 3.x (validator
 * internals) classpath split. Producers serialize events with Jackson 2.x; the
 * resulting JSON String passes straight into the validator.
 *
 * <p><b>T-01-06 (security control):</b> the {@link SchemaRegistry} is built with
 * {@code fetchRemoteResources(false)} (the 3.0.2 default, made explicit) AND
 * a custom {@link ClasspathOnlySchemaLoader} as the sole resource loader. Any
 * {@code $ref} URI that is not a classpath URI causes the loader to throw
 * {@link RemoteRefRefusedException}, structurally preventing remote-fetch attacks.
 * The companion test {@code AbstractEventSchemaTestSecurityCheck} asserts this
 * control by attempting URIs with {@code https://}, {@code http://}, and
 * {@code file://} schemes.
 *
 * <p>Phase 1 ships the base class + EnvelopeSchemaSelfTest + the security
 * self-check; Phase 5+ producers add concrete subclasses.
 */
public abstract class AbstractEventSchemaTest {

    /** Classpath root under which all 9 saga schemas ship in the common-events JAR. */
    private static final String SCHEMAS_CLASSPATH_ROOT = "/saga-schemas/";

    /** Registry wired with classpath-only resource resolution — see T-01-06 control above. */
    private static final SchemaRegistry REGISTRY = buildClasspathOnlyRegistry();

    private static SchemaRegistry buildClasspathOnlyRegistry() {
        return SchemaRegistry.builder()
            .defaultDialectId(SpecificationVersion.DRAFT_2020_12.getDialectId())
            .schemaLoader(loaderBuilder ->
                loaderBuilder
                    // Disable remote fetching at the validator level (defense-in-depth
                    // alongside the custom ResourceLoader below).
                    .fetchRemoteResources(false)
                    // Replace the default resource loaders with our classpath-only loader.
                    .resourceLoaders(loaders -> {
                        loaders.values(list -> list.clear());
                        loaders.add(new ClasspathOnlySchemaLoader());
                    })
            )
            .build();
    }

    /**
     * Validate the {@code producedJson} string against the schema file
     * {@code /saga-schemas/{schemaFileName}} on the classpath.
     *
     * <p>Asserts (via AssertJ) that the schema produces ZERO validation errors.
     *
     * @param schemaFileName e.g. {@code "envelope.schema.json"} or {@code "order-created.schema.json"}
     * @param producedJson   the JSON string produced by the saga publisher (already serialized)
     */
    protected void assertEventValid(String schemaFileName, String producedJson) {
        // Classpath-only schema lookup (T-01-06). Equivalent to:
        //   getClass().getResourceAsStream("/saga-schemas/" + schemaFileName)
        // — written via concatenation so no Phase 5+ subclass can pass an absolute path.
        String resourcePath = SCHEMAS_CLASSPATH_ROOT + schemaFileName;
        try (InputStream stream = getClass().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                fail("Schema not found on classpath: " + resourcePath);
                return; // unreachable
            }
            Schema schema = REGISTRY.getSchema(stream, InputFormat.JSON);
            List<Error> errors = schema.validate(producedJson, InputFormat.JSON);
            assertThat(errors)
                .as("Validation errors for %s: %s", schemaFileName, errors)
                .isEmpty();
        } catch (java.io.IOException ioe) {
            fail("IO error reading schema " + resourcePath + ": " + ioe.getMessage());
        }
    }

    /**
     * Validate by passing a producer object — caller serializes via their own ObjectMapper
     * (typically the standard Jackson 2.x mapper) and forwards the resulting String here.
     * This convenience helper exists for the EnvelopeSchemaSelfTest where we want to
     * keep the JSON-string round-trip explicit.
     */
    protected static class ResourceLoaderInternals implements ResourceLoader {
        @Override
        public com.networknt.schema.resource.InputStreamSource getResource(
                com.networknt.schema.AbsoluteIri absoluteIri) {
            // Never reached in normal Phase 1 flow — kept here only so the class is loadable
            // in unit tests that introspect ResourceLoader implementations.
            return null;
        }
    }
}
