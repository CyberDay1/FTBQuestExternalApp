package dev.ftbq.editor.services.generator;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Describes high-level expectations for the generated chapter content.
 */
public record QuestDesignSpec(String theme,
                              List<String> difficultyCurve,
                              List<String> gatingRules,
                              List<String> progressionAxes,
                              Set<String> itemBlacklist,
                              List<String> allowedTasks,
                              int chapterLength,
                              int rewardBudget) {

    public QuestDesignSpec {
        Objects.requireNonNull(theme, "theme");
        difficultyCurve = List.copyOf(Objects.requireNonNull(difficultyCurve, "difficultyCurve"));
        gatingRules = List.copyOf(Objects.requireNonNull(gatingRules, "gatingRules"));
        progressionAxes = List.copyOf(Objects.requireNonNull(progressionAxes, "progressionAxes"));
        itemBlacklist = Set.copyOf(Objects.requireNonNull(itemBlacklist, "itemBlacklist"));
        allowedTasks = List.copyOf(Objects.requireNonNull(allowedTasks, "allowedTasks"));
        if (chapterLength <= 0) {
            throw new IllegalArgumentException("chapterLength must be positive");
        }
        if (rewardBudget < 0) {
            throw new IllegalArgumentException("rewardBudget must be non-negative");
        }
    }
}
