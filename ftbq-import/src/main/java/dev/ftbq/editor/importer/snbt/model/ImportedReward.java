package dev.ftbq.editor.importer.snbt.model;

import java.util.Map;

/**
 * Represents a parsed quest reward.
 */
public record ImportedReward(String type, Map<String, Object> properties) {
}
