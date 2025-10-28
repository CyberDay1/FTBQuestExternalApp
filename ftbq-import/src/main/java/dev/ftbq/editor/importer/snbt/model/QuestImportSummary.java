package dev.ftbq.editor.importer.snbt.model;

import java.util.List;
import java.util.Map;

/**
 * Summary of the quest import process.
 */
public record QuestImportSummary(List<String> addedChapters,
                                 List<String> mergedChapters,
                                 List<String> addedQuests,
                                 List<String> renamedIds,
                                 Map<String, String> idRemap,
                                 List<String> warnings,
                                 List<String> assetWarnings) {
}
