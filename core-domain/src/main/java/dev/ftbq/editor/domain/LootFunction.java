package dev.ftbq.editor.domain;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Objects;

/**
 * Mutates loot entries before they are granted.
 */
public record LootFunction(String type, Map<String, Object> parameters, ObjectNode extras) {

    public LootFunction {
        Objects.requireNonNull(type, "type");
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
        extras = Objects.requireNonNull(extras, "extras").deepCopy();
    }

    public LootFunction(String type, Map<String, Object> parameters) {
        this(type, parameters, JsonNodeFactory.instance.objectNode());
    }
}
