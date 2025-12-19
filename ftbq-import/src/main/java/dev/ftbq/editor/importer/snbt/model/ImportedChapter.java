package dev.ftbq.editor.importer.snbt.model;

import dev.ftbq.editor.domain.Visibility;
import java.util.List;
import java.util.Map;

/**
 * Represents an imported chapter.
 */
public record ImportedChapter(String id,
                              String title,
                              String description,
                              String groupId,
                              String icon,
                              String background,
                              Visibility visibility,
                              List<ImportedQuest> quests,
                              List<Map<String, Object>> images,
                              List<Map<String, Object>> questLinks,
                              Map<String, Object> properties) {
}
