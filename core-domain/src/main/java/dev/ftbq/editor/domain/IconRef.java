package dev.ftbq.editor.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Points to an icon resource.
 */
public record IconRef(String icon, Optional<String> relativePath) {

    public IconRef {
        Objects.requireNonNull(icon, "icon");
        relativePath = Objects.requireNonNull(relativePath, "relativePath");
    }

    public IconRef(String icon) {
        this(icon, Optional.empty());
    }
}
