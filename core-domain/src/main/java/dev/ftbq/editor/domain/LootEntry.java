package dev.ftbq.editor.domain;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;

/**
 * Single loot drop configuration.
 */
public record LootEntry(ItemRef item, double weight, ObjectNode extras) {

    public LootEntry {
        Objects.requireNonNull(item, "item");
        if (weight <= 0) {
            throw new IllegalArgumentException("weight must be positive");
        }
        extras = Objects.requireNonNull(extras, "extras").deepCopy();
    }

    public LootEntry(ItemRef item, double weight) {
        this(item, weight, JsonNodeFactory.instance.objectNode());
    }
}
