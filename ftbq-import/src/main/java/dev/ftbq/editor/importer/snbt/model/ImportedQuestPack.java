package dev.ftbq.editor.importer.snbt.model;

import java.util.List;
import java.util.Set;

/**
 * Parsed quest pack from SNBT input.
 */
public record ImportedQuestPack(String id,
                                String title,
                                long schemaVersion,
                                List<ImportedChapterGroup> chapterGroups,
                                List<ImportedChapter> chapters,
                                Set<String> referencedAssets,
                                List<String> warnings) {
}
