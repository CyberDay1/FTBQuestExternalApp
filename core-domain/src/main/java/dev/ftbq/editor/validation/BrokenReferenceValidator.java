package dev.ftbq.editor.validation;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.ItemReward;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.LootEntry;
import dev.ftbq.editor.domain.LootPool;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.validation.Validator.ItemResolver;

import java.util.ArrayList;
import java.util.List;

public final class BrokenReferenceValidator implements Validator {
    @Override
    public List<ValidationIssue> validate(QuestFile qf, ItemResolver resolver) {
        if (resolver == null) {
            return List.of();
        }

        List<ValidationIssue> issues = new ArrayList<>();

        for (Chapter chapter : qf.chapters()) {
            for (Quest quest : chapter.quests()) {
                String questPath = "chapters/" + chapter.id() + "/quests/" + quest.id();

                for (Task task : quest.tasks()) {
                    if (task instanceof ItemTask itemTask) {
                        String itemId = itemTask.item().itemId();
                        if (!resolver.exists(itemId)) {
                            issues.add(new ValidationIssue("WARNING", questPath + "/tasks",
                                    "Unknown item in task: " + itemId));
                        }
                    }
                }

                for (ItemReward reward : quest.itemRewards()) {
                    String itemId = reward.itemRef().itemId();
                    if (!resolver.exists(itemId)) {
                        issues.add(new ValidationIssue("WARNING", questPath + "/rewards",
                                "Unknown item in reward: " + itemId));
                    }
                }
            }
        }

        for (LootTable table : qf.lootTables()) {
            String tablePath = "lootTables/" + table.id();
            for (LootPool pool : table.pools()) {
                for (LootEntry entry : pool.entries()) {
                    String itemId = entry.item().itemId();
                    if (!resolver.exists(itemId)) {
                        issues.add(new ValidationIssue("WARNING", tablePath + "/pools/" + pool.name(),
                                "Unknown item in loot entry: " + itemId));
                    }
                }
            }
        }

        return issues;
    }
}
