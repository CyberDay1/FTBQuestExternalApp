package dev.ftbq.editor.importer.snbt.model;

import dev.ftbq.editor.domain.Visibility;
import java.util.List;
import java.util.Map;

/**
 * Represents an imported quest and its nested content.
 */
public record ImportedQuest(String id,
                            String title,
                            String description,
                            String icon,
                            Visibility visibility,
                            List<ImportedTask> tasks,
                            List<ImportedReward> rewards,
                            List<ImportedDependency> dependencies,
                            Map<String, Object> properties) {
}
