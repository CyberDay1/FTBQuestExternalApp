package dev.ftbq.editor.services.generator;

import dev.ftbq.editor.domain.AdvancementTask;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.RewardType;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.validation.ValidationIssue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies lightweight validation rules to AI generated chapters.
 */
public final class GeneratedContentValidator {

    public GenerationValidationReport validate(List<Chapter> chapters,
                                               QuestDesignSpec designSpec,
                                               GenerationContext context) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (chapters.isEmpty()) {
            issues.add(new ValidationIssue("error", "chapters", "Model did not produce any chapters."));
        }

        Set<String> existingQuestIds = new HashSet<>();
        context.questFile().chapters().forEach(chapter ->
                chapter.quests().forEach(quest -> existingQuestIds.add(quest.id())));

        Set<String> seenChapterIds = new HashSet<>();
        Set<String> generatedQuestIds = new HashSet<>();

        for (Chapter chapter : chapters) {
            if (!seenChapterIds.add(chapter.id())) {
                issues.add(new ValidationIssue("error", "chapter:" + chapter.id(), "Duplicate chapter id."));
            }
            if (chapter.quests().isEmpty()) {
                issues.add(new ValidationIssue("error", "chapter:" + chapter.id(), "Chapter contains no quests."));
            }
            if (chapter.quests().size() > designSpec.chapterLength()) {
                issues.add(new ValidationIssue("warning", "chapter:" + chapter.id(),
                        "Chapter exceeds desired quest count of " + designSpec.chapterLength()));
            }
            validateQuests(chapter, designSpec, existingQuestIds, generatedQuestIds, issues);
        }

        int rewardCount = chapters.stream()
                .flatMap(chapter -> chapter.quests().stream())
                .mapToInt(quest -> quest.rewards().size())
                .sum();
        if (rewardCount > designSpec.rewardBudget()) {
            issues.add(new ValidationIssue("warning", "rewards",
                    "Total rewards " + rewardCount + " exceed budget " + designSpec.rewardBudget()));
        }

        return GenerationValidationReport.fromIssues(issues);
    }

    private void validateQuests(Chapter chapter,
                                QuestDesignSpec designSpec,
                                Set<String> existingQuestIds,
                                Set<String> generatedQuestIds,
                                List<ValidationIssue> issues) {
        for (Quest quest : chapter.quests()) {
            if (!generatedQuestIds.add(quest.id())) {
                issues.add(new ValidationIssue("error", "quest:" + quest.id(), "Duplicate quest id in generated content."));
            }
            if (quest.title().isBlank()) {
                issues.add(new ValidationIssue("error", "quest:" + quest.id(), "Quest title cannot be blank."));
            }
            if (quest.description().isBlank()) {
                issues.add(new ValidationIssue("warning", "quest:" + quest.id(), "Quest description is empty."));
            }
            if (quest.tasks().isEmpty()) {
                issues.add(new ValidationIssue("error", "quest:" + quest.id(), "Quest must contain at least one task."));
            }
            for (Task task : quest.tasks()) {
                if (!designSpec.allowedTasks().contains(task.type())) {
                    issues.add(new ValidationIssue("error", "quest:" + quest.id(),
                            "Task type " + task.type() + " is not permitted."));
                }
                if (task instanceof ItemTask itemTask) {
                    String itemId = itemTask.item().itemId();
                    if (designSpec.itemBlacklist().contains(itemId)) {
                        issues.add(new ValidationIssue("error", "quest:" + quest.id(),
                                "Task references blacklisted item " + itemId));
                    }
                    if (!itemId.contains(":")) {
                        issues.add(new ValidationIssue("warning", "quest:" + quest.id(),
                                "Item id " + itemId + " is missing a namespace."));
                    }
                }
                if (task instanceof AdvancementTask advancementTask) {
                    if (!advancementTask.advancementId().contains(":")) {
                        issues.add(new ValidationIssue("error", "quest:" + quest.id(),
                                "Advancement id must include namespace: " + advancementTask.advancementId()));
                    }
                }
            }
            for (Reward reward : quest.rewards()) {
                if (reward.type() == RewardType.ITEM && reward.item().isPresent()) {
                    String itemId = reward.item().get().itemId();
                    if (designSpec.itemBlacklist().contains(itemId)) {
                        issues.add(new ValidationIssue("error", "quest:" + quest.id(),
                                "Reward references blacklisted item " + itemId));
                    }
                }
            }
            quest.dependencies().forEach(dependency -> {
                String questId = dependency.questId();
                if (!generatedQuestIds.contains(questId) && !existingQuestIds.contains(questId)) {
                    issues.add(new ValidationIssue("error", "quest:" + quest.id(),
                            "Dependency references unknown quest " + questId));
                }
            });
        }
    }
}
