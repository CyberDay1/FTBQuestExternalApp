package dev.ftbq.editor.importer.snbt.model;

/**
 * Represents a quest dependency relation.
 */
public record ImportedDependency(String questId, boolean required) {
}
