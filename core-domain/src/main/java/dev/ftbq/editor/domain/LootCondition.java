package dev.ftbq.editor.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Conditional logic for loot pools.
 */
public record LootCondition(String type, Map<String, Object> parameters) {

    public LootCondition {
        Objects.requireNonNull(type, "type");
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
    }
}
