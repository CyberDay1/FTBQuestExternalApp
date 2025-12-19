package dev.ftbq.editor.importer.snbt.service;

import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.importer.snbt.model.ImportedChapter;
import dev.ftbq.editor.importer.snbt.model.ImportedChapterGroup;
import dev.ftbq.editor.importer.snbt.model.ImportedDependency;
import dev.ftbq.editor.importer.snbt.model.ImportedQuest;
import dev.ftbq.editor.importer.snbt.model.ImportedQuestPack;
import dev.ftbq.editor.importer.snbt.model.ImportedReward;
import dev.ftbq.editor.importer.snbt.model.ImportedTask;
import dev.ftbq.editor.importer.snbt.parser.SnbtParseException;
import dev.ftbq.editor.importer.snbt.parser.SnbtParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reads SNBT quest data into the intermediate import model.
 */
public final class SnbtQuestPackReader {

    public ImportedQuestPack read(String snbtText) {
        Objects.requireNonNull(snbtText, "snbtText");
        Map<String, Object> root = SnbtParser.parseRootCompound(snbtText);
        List<String> warnings = new ArrayList<>();
        Set<String> assets = new LinkedHashSet<>();

        String id = stringValue(root, "id").orElse("quest_file");
        String title = stringValue(root, "title").orElse("Imported Quest Pack");
        long version = numberValue(root, "file_version").or(() -> numberValue(root, "version")).orElse(0L);

        List<ImportedChapterGroup> groups = parseChapterGroups(root.get("chapter_groups"), warnings, assets);
        List<ImportedChapter> chapters = parseChapters(root.get("chapters"), warnings, assets);

        return new ImportedQuestPack(id, title, version, groups, chapters, assets, warnings);
    }

    private List<ImportedChapterGroup> parseChapterGroups(Object raw,
                                                          List<String> warnings,
                                                          Set<String> assets) {
        List<Map<String, Object>> entries = asListOfCompounds(raw);
        List<ImportedChapterGroup> groups = new ArrayList<>(entries.size());
        for (Map<String, Object> entry : entries) {
            String id = stringValue(entry, "id").orElseGet(() -> Long.toString(numberValue(entry, "id").orElse(0L)));
            String title = stringValue(entry, "title").orElse("Imported Group " + id);
            String icon = stringValue(entry, "icon").orElse("minecraft:book");
            assets.add(icon);
            List<String> chapterIds = stringList(entry.getOrDefault("chapter_ids", entry.get("chapters")));
            if (chapterIds.isEmpty()) {
                warnings.add("Chapter group " + id + " does not list any chapters.");
            }
            Visibility visibility = visibilityValue(entry.get("visibility"));
            groups.add(new ImportedChapterGroup(id, title, icon, chapterIds, visibility));
        }
        return groups;
    }

    private List<ImportedChapter> parseChapters(Object raw,
                                                List<String> warnings,
                                                Set<String> assets) {
        List<Map<String, Object>> entries = asListOfCompounds(raw);
        List<ImportedChapter> chapters = new ArrayList<>(entries.size());
        for (Map<String, Object> entry : entries) {
            String id = stringValue(entry, "id").orElseGet(() -> Long.toString(numberValue(entry, "id").orElse(0L)));
            String title = stringValue(entry, "title").orElse("Imported Chapter " + id);
            String description = collapseDescription(entry.get("description"));
            String groupId = stringValue(entry, "group").or(() -> stringValue(entry, "group_id"))
                    .orElse(null);
            String icon = stringValue(entry, "icon").orElse("minecraft:book");
            assets.add(icon);
            String background = stringValue(entry, "background").orElse("minecraft:textures/gui/default.png");
            assets.add(background);
            Visibility visibility = visibilityValue(entry.get("visibility"));
            List<ImportedQuest> quests = parseQuests(entry.get("quests"), warnings, assets);
            List<Map<String, Object>> images = parseImages(entry.get("images"), assets);
            List<Map<String, Object>> questLinks = parseQuestLinks(entry.get("quest_links"));
            Map<String, Object> properties = remainingProperties(entry,
                    List.of("id", "title", "description", "group", "group_id", "icon", "background", "visibility", "quests", "images", "quest_links"));
            chapters.add(new ImportedChapter(id, title, description, groupId, icon, background, visibility, quests, images, questLinks, properties));
        }
        return chapters;
    }

    private List<Map<String, Object>> parseImages(Object raw, Set<String> assets) {
        List<Map<String, Object>> entries = asListOfCompounds(raw);
        for (Map<String, Object> entry : entries) {
            stringValue(entry, "image").ifPresent(assets::add);
        }
        return entries;
    }

    private List<Map<String, Object>> parseQuestLinks(Object raw) {
        return asListOfCompounds(raw);
    }

    private List<ImportedQuest> parseQuests(Object raw,
                                            List<String> warnings,
                                            Set<String> assets) {
        List<Map<String, Object>> entries = asListOfCompounds(raw);
        List<ImportedQuest> quests = new ArrayList<>(entries.size());
        for (Map<String, Object> entry : entries) {
            String id = stringValue(entry, "id").orElseGet(() -> Long.toString(numberValue(entry, "id").orElse(0L)));
            String title = stringValue(entry, "title").orElse("Quest " + id);
            String description = collapseDescription(entry.get("description"));
            String icon = stringValue(entry, "icon").orElse("minecraft:book");
            assets.add(icon);
            Visibility visibility = visibilityValue(entry.get("visibility"));
            List<ImportedTask> tasks = parseTasks(entry.get("tasks"));
            List<ImportedReward> rewards = parseRewards(entry.get("rewards"));
            List<ImportedDependency> dependencies = parseDependencies(entry.get("dependencies"));
            Map<String, Object> properties = remainingProperties(entry,
                    List.of("id", "title", "description", "icon", "visibility", "tasks", "rewards", "dependencies"));
            quests.add(new ImportedQuest(id, title, description, icon, visibility, tasks, rewards, dependencies, properties));
            if (tasks.isEmpty()) {
                warnings.add("Quest " + id + " has no tasks.");
            }
        }
        return quests;
    }

    private List<ImportedTask> parseTasks(Object raw) {
        List<Map<String, Object>> entries = asListOfCompounds(raw);
        List<ImportedTask> tasks = new ArrayList<>(entries.size());
        for (Map<String, Object> entry : entries) {
            String type = stringValue(entry, "type").orElse("custom");
            tasks.add(new ImportedTask(type, new LinkedHashMap<>(entry)));
        }
        return tasks;
    }

    private List<ImportedReward> parseRewards(Object raw) {
        List<Map<String, Object>> entries = asListOfCompounds(raw);
        List<ImportedReward> rewards = new ArrayList<>(entries.size());
        for (Map<String, Object> entry : entries) {
            String type = stringValue(entry, "type").orElse("custom");
            rewards.add(new ImportedReward(type, new LinkedHashMap<>(entry)));
        }
        return rewards;
    }

    private List<ImportedDependency> parseDependencies(Object raw) {
        List<ImportedDependency> dependencies = new ArrayList<>();
        if (raw == null) {
            return dependencies;
        }
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                dependencies.add(parseDependencyEntry(item));
            }
        } else {
            dependencies.add(parseDependencyEntry(raw));
        }
        return dependencies;
    }

    private ImportedDependency parseDependencyEntry(Object entry) {
        if (entry instanceof Map<?, ?> map) {
            Map<String, Object> compound = castCompound(map);
            String questId = stringValue(compound, "quest").or(() -> stringValue(compound, "id"))
                    .orElseGet(() -> Long.toString(numberValue(compound, "id").orElse(0L)));
            boolean required = booleanValue(compound.get("required"));
            return new ImportedDependency(questId, required);
        }
        if (entry instanceof Number number) {
            return new ImportedDependency(Long.toString(number.longValue()), true);
        }
        if (entry instanceof String id) {
            return new ImportedDependency(id, true);
        }
        throw new SnbtParseException("Unsupported dependency entry: " + entry);
    }

    private Map<String, Object> remainingProperties(Map<String, Object> source, List<String> reservedKeys) {
        Map<String, Object> copy = new LinkedHashMap<>(source);
        for (String key : reservedKeys) {
            copy.remove(key);
        }
        return copy;
    }

    private Optional<String> stringValue(Map<String, Object> map, String key) {
        return Optional.ofNullable(map.get(key)).flatMap(this::stringValue);
    }

    private Optional<String> stringValue(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof String string) {
            return Optional.of(string);
        }
        if (value instanceof Number number) {
            return Optional.of(Long.toString(number.longValue()));
        }
        if (value instanceof Boolean bool) {
            return Optional.of(Boolean.toString(bool));
        }
        return Optional.of(value.toString());
    }

    private Optional<Long> numberValue(Map<String, Object> map, String key) {
        return Optional.ofNullable(map.get(key)).flatMap(this::numberValue);
    }

    private Optional<Long> numberValue(Object value) {
        if (value instanceof Number number) {
            return Optional.of(number.longValue());
        }
        if (value instanceof String string) {
            try {
                return Optional.of(Long.parseLong(string));
            } catch (NumberFormatException ignored) {
            }
        }
        return Optional.empty();
    }

    private Visibility visibilityValue(Object raw) {
        if (raw == null) {
            return Visibility.VISIBLE;
        }
        if (raw instanceof String string) {
            try {
                return Visibility.valueOf(string.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return Visibility.VISIBLE;
            }
        }
        if (raw instanceof Number number) {
            long value = number.longValue();
            if (value <= 0) {
                return Visibility.VISIBLE;
            }
            if (value == 1) {
                return Visibility.HIDDEN;
            }
            return Visibility.SECRET;
        }
        return Visibility.VISIBLE;
    }

    private String collapseDescription(Object raw) {
        if (raw == null) {
            return "";
        }
        if (raw instanceof String string) {
            return string;
        }
        if (raw instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object entry : list) {
                parts.add(stringValue(entry).orElse(""));
            }
            return String.join("\n", parts);
        }
        return raw.toString();
    }

    private boolean booleanValue(Object raw) {
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        if (raw instanceof String string) {
            return Boolean.parseBoolean(string);
        }
        return false;
    }

    private List<String> stringList(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> values = new ArrayList<>(list.size());
            for (Object entry : list) {
                values.add(stringValue(entry).orElse(""));
            }
            return values;
        }
        if (raw instanceof String string) {
            return List.of(string);
        }
        if (raw instanceof Number number) {
            return List.of(Long.toString(number.longValue()));
        }
        return List.of();
    }

    private List<Map<String, Object>> asListOfCompounds(Object raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    result.add(castCompound(map));
                } else {
                    throw new SnbtParseException("Expected compound entry but found: " + entry);
                }
            }
        } else if (raw instanceof Map<?, ?> single) {
            result.add(castCompound(single));
        } else if (raw != null) {
            throw new SnbtParseException("Expected list of compounds but found: " + raw);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castCompound(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
