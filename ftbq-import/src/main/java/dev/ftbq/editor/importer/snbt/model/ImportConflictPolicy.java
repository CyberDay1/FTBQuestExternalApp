package dev.ftbq.editor.importer.snbt.model;

/**
 * Defines how to handle identifier conflicts during quest import.
 */
public enum ImportConflictPolicy {
    SKIP,
    MERGE_BY_ID,
    RENAME,
    NEW_IDS
}
