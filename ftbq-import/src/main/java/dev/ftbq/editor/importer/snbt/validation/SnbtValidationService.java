package dev.ftbq.editor.importer.snbt.validation;

import dev.ftbq.editor.importer.snbt.parser.SnbtParseException;
import dev.ftbq.editor.importer.snbt.parser.SnbtParser;
import dev.ftbq.editor.validation.ValidationIssue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SnbtValidationService {
    private static final String SEVERITY_ERROR = "ERROR";

    private final SnbtSchemaValidator schemaValidator = new SnbtSchemaValidator(SnbtSchemas.questPack());

    public SnbtValidationReport validate(String snbtText) {
        Objects.requireNonNull(snbtText, "snbtText");
        List<ValidationIssue> issues = new ArrayList<>();
        Map<String, Object> root;
        try {
            root = SnbtParser.parseRootCompound(snbtText);
        } catch (SnbtParseException ex) {
            issues.add(new ValidationIssue(SEVERITY_ERROR, ValidationPath.root().toString(), "SNBT parse error: " + ex.getMessage()));
            return new SnbtValidationReport(issues);
        }

        issues.addAll(schemaValidator.validate(root));
        validateStructure(root, issues);
        return new SnbtValidationReport(issues);
    }

    @SuppressWarnings("unchecked")
    private void validateStructure(Map<String, Object> root, List<ValidationIssue> issues) {
        Map<String, ValidationPath> chapterIds = new LinkedHashMap<>();
        Map<String, ValidationPath> questIds = new LinkedHashMap<>();
        Map<String, ValidationPath> groupIds = new LinkedHashMap<>();
        Map<String, ValidationPath> lootTableIds = new LinkedHashMap<>();
        List<GroupReference> groupReferences = new ArrayList<>();
        List<QuestDependencies> questDependencies = new ArrayList<>();
        List<LootTableRewardRef> lootTableRewards = new ArrayList<>();

        Object groupsRaw = root.get("chapter_groups");
        if (groupsRaw instanceof List<?> groups) {
            for (int i = 0; i < groups.size(); i++) {
                Object entry = groups.get(i);
                if (entry instanceof Map<?, ?> map) {
                    Map<String, Object> group = castCompound(map);
                    String id = normalizeId(group.get("id"));
                    ValidationPath basePath = ValidationPath.root().property("chapter_groups").index(i);
                    if (id != null) {
                        checkUniqueness(id, basePath.property("id"), groupIds, issues, "chapter group");
                    }
                    List<String> references = collectIdList(group.get("chapter_ids"));
                    String referenceProperty = "chapter_ids";
                    if (references.isEmpty()) {
                        references = collectIdList(group.get("chapters"));
                        referenceProperty = "chapters";
                    }
                    if (references.isEmpty()) {
                        issues.add(new ValidationIssue(SEVERITY_ERROR,
                                basePath.property(referenceProperty).toString(),
                                "Chapter group must reference at least one chapter"));
                    }
                    groupReferences.add(new GroupReference(basePath, referenceProperty, references));
                }
            }
        }

        Object lootTablesRaw = root.get("loot_tables");
        if (lootTablesRaw instanceof List<?> tables) {
            for (int i = 0; i < tables.size(); i++) {
                Object entry = tables.get(i);
                if (entry instanceof Map<?, ?> map) {
                    Map<String, Object> table = castCompound(map);
                    String id = normalizeString(table.get("id"));
                    ValidationPath idPath = ValidationPath.root().property("loot_tables").index(i).property("id");
                    if (id != null) {
                        checkUniqueness(id, idPath, lootTableIds, issues, "loot table");
                    }
                }
            }
        }

        Object chaptersRaw = root.get("chapters");
        if (chaptersRaw instanceof List<?> chapters) {
            for (int i = 0; i < chapters.size(); i++) {
                Object entry = chapters.get(i);
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> chapter = castCompound(map);
                String chapterId = normalizeId(chapter.get("id"));
                ValidationPath chapterPath = ValidationPath.root().property("chapters").index(i);
                if (chapterId != null) {
                    checkUniqueness(chapterId, chapterPath.property("id"), chapterIds, issues, "chapter");
                }
                List<?> questsRaw = optionalList(chapter.get("quests"));
                if (questsRaw == null) {
                    continue;
                }
                for (int q = 0; q < questsRaw.size(); q++) {
                    Object questEntry = questsRaw.get(q);
                    if (!(questEntry instanceof Map<?, ?> questMap)) {
                        continue;
                    }
                    Map<String, Object> quest = castCompound(questMap);
                    String questId = normalizeId(quest.get("id"));
                    ValidationPath questPath = chapterPath.property("quests").index(q);
                    if (questId != null) {
                        checkUniqueness(questId, questPath.property("id"), questIds, issues, "quest");
                    }
                    List<?> tasksRaw = optionalList(quest.get("tasks"));
                    if (tasksRaw != null) {
                        for (int t = 0; t < tasksRaw.size(); t++) {
                            Object taskEntry = tasksRaw.get(t);
                            if (taskEntry instanceof Map<?, ?> taskMap) {
                                Map<String, Object> task = castCompound(taskMap);
                                validateTask(task, questPath.property("tasks").index(t), issues);
                            }
                        }
                    }
                    List<?> dependenciesRaw = optionalList(quest.get("dependencies"));
                    if (dependenciesRaw != null) {
                        questDependencies.add(new QuestDependencies(questPath.property("dependencies"), dependenciesRaw));
                    }
                    List<?> rewardsRaw = optionalList(quest.get("rewards"));
                    if (rewardsRaw != null) {
                        for (int r = 0; r < rewardsRaw.size(); r++) {
                            Object rewardEntry = rewardsRaw.get(r);
                            if (rewardEntry instanceof Map<?, ?> rewardMap) {
                                Map<String, Object> reward = castCompound(rewardMap);
                                validateReward(reward, questPath.property("rewards").index(r), issues);
                                String type = normalizeString(reward.get("type"));
                                if ("loot_table".equals(type)) {
                                    lootTableRewards.add(new LootTableRewardRef(questPath.property("rewards").index(r), reward));
                                }
                            }
                        }
                    }
                }
            }
        }

        // Validate chapter group references against known chapter IDs
        for (GroupReference reference : groupReferences) {
            for (int index = 0; index < reference.chapterIds().size(); index++) {
                String chapterId = reference.chapterIds().get(index);
                if (!chapterIds.containsKey(chapterId)) {
                    issues.add(new ValidationIssue(SEVERITY_ERROR,
                            reference.path().property(reference.property()).index(index).toString(),
                            "Chapter group references unknown chapter id '" + chapterId + "'"));
                }
            }
        }

        // Validate quest dependencies
        for (QuestDependencies dependencies : questDependencies) {
            List<?> values = dependencies.entries();
            for (int i = 0; i < values.size(); i++) {
                Object entry = values.get(i);
                Optional<String> dependencyId = extractDependencyId(entry);
                if (dependencyId.isEmpty()) {
                    issues.add(new ValidationIssue(SEVERITY_ERROR,
                            dependencies.path().index(i).toString(),
                            "Dependency entry must provide a quest id"));
                    continue;
                }
                String id = dependencyId.get();
                if (!questIds.containsKey(id)) {
                    issues.add(new ValidationIssue(SEVERITY_ERROR,
                            dependencies.path().index(i).toString(),
                            "Dependency references unknown quest id '" + id + "'"));
                }
            }
        }

        // Validate loot table reward references
        for (LootTableRewardRef reward : lootTableRewards) {
            Object tableIdRaw = reward.value().get("table");
            if (!(tableIdRaw instanceof String tableId) || tableId.isBlank()) {
                issues.add(new ValidationIssue(SEVERITY_ERROR,
                        reward.path().property("table").toString(),
                        "Loot table rewards must specify a non-empty 'table' property"));
                continue;
            }
            if (!lootTableIds.containsKey(tableId)) {
                issues.add(new ValidationIssue(SEVERITY_ERROR,
                        reward.path().property("table").toString(),
                        "Loot table reward references unknown table '" + tableId + "'"));
            }
        }
    }

    private void checkUniqueness(String id,
                                  ValidationPath path,
                                  Map<String, ValidationPath> registry,
                                  List<ValidationIssue> issues,
                                  String type) {
        ValidationPath existing = registry.putIfAbsent(id, path);
        if (existing != null) {
            issues.add(new ValidationIssue(SEVERITY_ERROR,
                    path.toString(),
                    type + " id '" + id + "' is already defined at " + existing));
        }
    }

    private Map<String, Object> castCompound(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    private List<String> collectIdList(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object entry : list) {
                String normalized = normalizeId(entry);
                if (normalized != null) {
                    values.add(normalized);
                }
            }
        } else {
            String normalized = normalizeId(raw);
            if (normalized != null) {
                values.add(normalized);
            }
        }
        return values;
    }

    private Optional<String> extractDependencyId(Object entry) {
        if (entry instanceof Map<?, ?> map) {
            Map<String, Object> compound = castCompound(map);
            String questId = normalizeId(Optional.ofNullable(compound.get("quest")).orElse(compound.get("id")));
            return Optional.ofNullable(questId);
        }
        return Optional.ofNullable(normalizeId(entry));
    }

    private List<?> optionalList(Object raw) {
        if (raw instanceof List<?> list) {
            return list;
        }
        return null;
    }

    private String normalizeId(Object raw) {
        if (raw instanceof Number number) {
            return Long.toString(number.longValue());
        }
        if (raw instanceof String string) {
            return string;
        }
        return null;
    }

    private String normalizeString(Object raw) {
        return raw instanceof String string ? string : null;
    }

    private void validateTask(Map<String, Object> task, ValidationPath path, List<ValidationIssue> issues) {
        String type = normalizeString(task.get("type"));
        if (type == null) {
            return;
        }
        switch (type.toLowerCase(Locale.ROOT)) {
            case "item" -> {
                Object itemRaw = task.get("item");
                if (!(itemRaw instanceof Map<?, ?> itemMap)) {
                    issues.add(new ValidationIssue(SEVERITY_ERROR,
                            path.property("item").toString(),
                            "Item tasks must define an 'item' compound"));
                } else {
                    Map<String, Object> item = castCompound(itemMap);
                    if (normalizeString(item.get("id")) == null) {
                        issues.add(new ValidationIssue(SEVERITY_ERROR,
                                path.property("item").property("id").toString(),
                                "Item tasks require an item id"));
                    }
                }
            }
            case "advancement" -> {
                if (normalizeString(task.get("advancement")) == null) {
                    issues.add(new ValidationIssue(SEVERITY_ERROR,
                            path.property("advancement").toString(),
                            "Advancement tasks must provide an 'advancement' id"));
                }
            }
            case "location" -> {
                requireString(task, "dimension", path, issues, "Location tasks require a dimension id");
                requireNumber(task, "x", path, issues, "Location tasks require numeric coordinates");
                requireNumber(task, "y", path, issues, "Location tasks require numeric coordinates");
                requireNumber(task, "z", path, issues, "Location tasks require numeric coordinates");
                requireNumber(task, "radius", path, issues, "Location tasks require a radius value");
            }
            default -> {
            }
        }
    }

    private void validateReward(Map<String, Object> reward, ValidationPath path, List<ValidationIssue> issues) {
        String type = normalizeString(reward.get("type"));
        if (type == null) {
            return;
        }
        switch (type.toLowerCase(Locale.ROOT)) {
            case "item" -> {
                Object itemRaw = reward.get("item");
                if (!(itemRaw instanceof Map<?, ?> itemMap)) {
                    issues.add(new ValidationIssue(SEVERITY_ERROR,
                            path.property("item").toString(),
                            "Item rewards must define an 'item' compound"));
                } else {
                    Map<String, Object> item = castCompound(itemMap);
                    if (normalizeString(item.get("id")) == null) {
                        issues.add(new ValidationIssue(SEVERITY_ERROR,
                                path.property("item").property("id").toString(),
                                "Item rewards require an item id"));
                    }
                }
            }
            case "loot_table" -> requireString(reward, "table", path, issues, "Loot table rewards require a 'table' id");
            case "xp_levels", "xp_amount" -> requireNumber(reward, "amount", path, issues, "XP rewards require an 'amount'");
            case "command" -> requireString(reward, "command", path, issues, "Command rewards require a 'command' value");
            default -> {
            }
        }
    }

    private void requireString(Map<String, Object> value,
                               String key,
                               ValidationPath path,
                               List<ValidationIssue> issues,
                               String message) {
        if (normalizeString(value.get(key)) == null) {
            issues.add(new ValidationIssue(SEVERITY_ERROR, path.property(key).toString(), message));
        }
    }

    private void requireNumber(Map<String, Object> value,
                               String key,
                               ValidationPath path,
                               List<ValidationIssue> issues,
                               String message) {
        if (!(value.get(key) instanceof Number)) {
            issues.add(new ValidationIssue(SEVERITY_ERROR, path.property(key).toString(), message));
        }
    }

    private record GroupReference(ValidationPath path, String property, List<String> chapterIds) {
    }

    private record QuestDependencies(ValidationPath path, List<?> entries) {
    }

    private record LootTableRewardRef(ValidationPath path, Map<String, Object> value) {
    }
}
