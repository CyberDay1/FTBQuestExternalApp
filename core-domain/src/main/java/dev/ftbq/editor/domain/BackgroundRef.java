package dev.ftbq.editor.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Reference to a background texture with layout hints.
 */
public record BackgroundRef(String texture,
                             Optional<BackgroundAlignment> alignment,
                             Optional<BackgroundRepeat> repeat) {

    public BackgroundRef {
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(repeat, "repeat");
    }
}
