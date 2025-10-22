package dev.ftbq.editor.domain;

import java.util.Objects;

/**
 * Single loot drop configuration.
 */
public record LootEntry(ItemRef item, double weight) {

    public LootEntry {
        Objects.requireNonNull(item, "item");
        if (weight <= 0) {
            throw new IllegalArgumentException("weight must be positive");
        }
    }
}
