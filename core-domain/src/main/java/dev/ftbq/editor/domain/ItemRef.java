package dev.ftbq.editor.domain;

import java.util.Objects;

/**
 * Reference to a concrete item stack.
 */
public record ItemRef(String itemId, int count) {

    public ItemRef {
        Objects.requireNonNull(itemId, "itemId");
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}
