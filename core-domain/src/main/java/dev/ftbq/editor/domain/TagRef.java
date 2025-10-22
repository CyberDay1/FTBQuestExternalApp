package dev.ftbq.editor.domain;

import java.util.Objects;

/**
 * Reference to a registry tag.
 */
public record TagRef(String tagId) {

    public TagRef {
        Objects.requireNonNull(tagId, "tagId");
    }
}
