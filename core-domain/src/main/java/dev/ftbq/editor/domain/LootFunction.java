package dev.ftbq.editor.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Mutates loot entries before they are granted.
 */
public record LootFunction(String type, Map<String, Object> parameters) {

    public LootFunction {
        Objects.requireNonNull(type, "type");
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
    }
}
