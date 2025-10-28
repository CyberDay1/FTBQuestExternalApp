package dev.ftbq.editor.importer.snbt.model;

import dev.ftbq.editor.domain.QuestFile;

/**
 * Result of applying an imported quest pack to an existing quest file.
 */
public record QuestImportResult(QuestFile questFile, QuestImportSummary summary) {
}
