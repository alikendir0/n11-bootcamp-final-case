package com.n11.events;

import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.resource.InputStreamSource;
import com.networknt.schema.resource.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;

/**
 * {@link ResourceLoader} that ONLY resolves classpath URIs. Any URI starting with
 * {@code http://}, {@code https://}, {@code file://}, or any other absolute scheme
 * triggers a {@link RemoteRefRefusedException} — the saga drift gate refuses to
 * fetch untrusted schema content (T-01-06 mitigation).
 *
 * <p>networknt 3.0.2 API note (Rule-1 deviation from PLAN code): the original Plan
 * 01-04 used {@code SchemaLoader} as the interface name; in 3.0.2 {@code SchemaLoader}
 * is a concrete class, and the corresponding interface is {@link ResourceLoader}.
 * The behaviour is identical: scoped to a single URI scheme namespace.
 *
 * <p>Wired into {@link AbstractEventSchemaTest}'s registry via
 * {@code .resourceLoaders(loaders -> { loaders.values(list -> list.clear()); loaders.add(new ClasspathOnlySchemaLoader()); })}.
 */
final class ClasspathOnlySchemaLoader implements ResourceLoader {

    private static final String CLASSPATH_PREFIX = "classpath:";

    @Override
    public InputStreamSource getResource(AbsoluteIri absoluteIri) {
        String uri = absoluteIri == null ? "" : absoluteIri.toString();
        if (uri.startsWith(CLASSPATH_PREFIX)) {
            String path = uri.substring(CLASSPATH_PREFIX.length());
            String resourcePath = path.startsWith("/") ? path : "/" + path;
            return () -> {
                InputStream stream = getClass().getResourceAsStream(resourcePath);
                if (stream == null) {
                    throw new IOException("Classpath resource not found: " + resourcePath);
                }
                return stream;
            };
        }
        // Refuse every non-classpath scheme — this is the structural T-01-06 control.
        throw new RemoteRefRefusedException(uri);
    }
}
