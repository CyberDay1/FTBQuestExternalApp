package dev.ftbq.editor.resources;

import java.util.Locale;
import java.util.Objects;

/**
 * Representation of a Minecraft style resource identifier ({@code namespace:path}).
 */
public record ResourceId(String namespace, String path) {

    public ResourceId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        namespace = namespace.trim();
        path = path.trim();
        if (namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace must not be empty");
        }
        if (path.isEmpty()) {
            throw new IllegalArgumentException("path must not be empty");
        }
        namespace = namespace.toLowerCase(Locale.ROOT);
        path = path.toLowerCase(Locale.ROOT);
    }

    /**
     * Parse a textual identifier using {@code minecraft} as the default namespace.
     *
     * @param value textual value to parse
     * @return parsed {@link ResourceId}
     */
    public static ResourceId fromString(String value) {
        return fromString(value, "minecraft");
    }

    /**
     * Parse a textual identifier using the supplied namespace when none is present.
     *
     * @param value            textual value to parse
     * @param defaultNamespace namespace to use when {@code value} omits it
     * @return parsed {@link ResourceId}
     */
    public static ResourceId fromString(String value, String defaultNamespace) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(defaultNamespace, "defaultNamespace");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        int colon = trimmed.indexOf(':');
        if (colon >= 0) {
            String namespace = trimmed.substring(0, colon);
            String path = trimmed.substring(colon + 1);
            return new ResourceId(namespace, path);
        }
        return new ResourceId(defaultNamespace, trimmed);
    }

    /**
     * Return a new {@link ResourceId} with the same namespace and a different path.
     *
     * @param newPath replacement path
     * @return updated {@link ResourceId}
     */
    public ResourceId withPath(String newPath) {
        return new ResourceId(namespace, newPath);
    }

    @Override
    public String toString() {
        return namespace + ':' + path;
    }
}

