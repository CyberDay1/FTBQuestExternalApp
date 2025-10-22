package dev.ftbq.editor.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Reference to a background texture with layout hints.
 */
public record BackgroundRef(String texture,
                             Optional<String> relativePath,
                             Optional<BackgroundAlignment> alignment,
                             Optional<BackgroundRepeat> repeat) {

    public BackgroundRef {
        Objects.requireNonNull(texture, "texture");
        relativePath = Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(repeat, "repeat");
    }

    public BackgroundRef(String texture,
                          Optional<BackgroundAlignment> alignment,
                          Optional<BackgroundRepeat> repeat) {
        this(texture, Optional.empty(), alignment, repeat);
    }

    public BackgroundRef(String texture) {
        this(texture, Optional.empty(), Optional.empty(), Optional.empty());
    }
}
