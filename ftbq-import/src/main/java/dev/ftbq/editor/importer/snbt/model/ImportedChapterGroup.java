package dev.ftbq.editor.importer.snbt.model;

import dev.ftbq.editor.domain.Visibility;
import java.util.List;

/**
 * Represents an imported chapter group.
 */
public record ImportedChapterGroup(String id,
                                   String title,
                                   String icon,
                                   List<String> chapterIds,
                                   Visibility visibility) {
}
