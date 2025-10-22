package dev.ftbq.editor.domain;

import java.util.Objects;

/**
 * Points to an icon resource.
 */
public record IconRef(String icon) {

    public IconRef {
        Objects.requireNonNull(icon, "icon");
    }
}
