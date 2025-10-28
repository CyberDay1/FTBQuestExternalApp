package dev.ftbq.editor.importer.snbt.model;

import java.util.Map;

/**
 * Represents a parsed quest task.
 */
public record ImportedTask(String type, Map<String, Object> properties) {
}
