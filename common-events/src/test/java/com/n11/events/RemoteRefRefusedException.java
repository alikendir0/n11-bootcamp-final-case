package com.n11.events;

/**
 * Thrown by {@link ClasspathOnlySchemaLoader} when a schema attempts to
 * resolve a non-classpath {@code $ref} (http, https, file, etc.).
 * T-01-06 mitigation.
 */
public class RemoteRefRefusedException extends RuntimeException {
    public RemoteRefRefusedException(String uri) {
        super("Remote $ref refused (T-01-06 saga drift gate): " + uri);
    }
}
