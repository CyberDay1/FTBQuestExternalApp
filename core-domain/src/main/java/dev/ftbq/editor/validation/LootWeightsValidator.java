package dev.ftbq.editor.validation;

import dev.ftbq.editor.domain.LootEntry;
import dev.ftbq.editor.domain.LootPool;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.validation.Validator.ItemResolver;

import java.util.ArrayList;
import java.util.List;

public final class LootWeightsValidator implements Validator {
    @Override
    public List<ValidationIssue> validate(QuestFile qf, ItemResolver resolver) {
        List<ValidationIssue> issues = new ArrayList<>();

        for (LootTable table : qf.lootTables()) {
            String tablePath = "lootTables/" + table.id();

            if (table.pools().isEmpty()) {
                issues.add(new ValidationIssue("WARNING", tablePath,
                        "Loot table has no pools"));
                continue;
            }

            for (LootPool pool : table.pools()) {
                String poolPath = tablePath + "/pools/" + pool.name();

                if (pool.entries().isEmpty()) {
                    issues.add(new ValidationIssue("WARNING", poolPath,
                            "Loot pool has no entries"));
                    continue;
                }

                double totalWeight = 0;
                for (LootEntry entry : pool.entries()) {
                    if (entry.weight() <= 0) {
                        issues.add(new ValidationIssue("ERROR", poolPath,
                                "Loot entry has invalid weight: " + entry.weight()));
                    }
                    totalWeight += entry.weight();
                }

                if (totalWeight <= 0) {
                    issues.add(new ValidationIssue("ERROR", poolPath,
                            "Loot pool has zero total weight"));
                }
            }
        }

        return issues;
    }
}
