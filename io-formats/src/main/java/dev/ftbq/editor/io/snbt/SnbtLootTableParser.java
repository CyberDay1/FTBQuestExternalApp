package dev.ftbq.editor.io.snbt;

import dev.ftbq.editor.importer.snbt.parser.SnbtParseException;
import dev.ftbq.editor.importer.snbt.parser.SnbtParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Parses loot table SNBT definitions to extract item entries that can be
 * presented in the quest editor UI.
 */
public class SnbtLootTableParser {

    private static final String DEFAULT_ICON = "minecraft:book";
    private static final int DEFAULT_WEIGHT = 100;

    /**
     * Extracts loot table metadata from the provided SNBT definition.
     *
     * @param snbt SNBT loot table text
     * @return parsed loot table data including icon and items
     */
    public LootTableData parse(String snbt) {
        if (snbt == null || snbt.isBlank()) {
            return new LootTableData(DEFAULT_ICON, List.of());
        }
        Map<String, Object> root;
        try {
            root = SnbtParser.parseRootCompound(snbt);
        } catch (SnbtParseException | IllegalArgumentException ex) {
            return new LootTableData(DEFAULT_ICON, List.of());
        }

        String icon = Optional.ofNullable(asString(root.get("icon")))
                .filter(value -> !value.isBlank())
                .orElse(DEFAULT_ICON);

        List<LootTableItem> items = parseItemsArray(root.get("items"));
        if (items.isEmpty()) {
            items = parseLegacyPools(root);
        }
        return new LootTableData(icon, items);
    }

    /**
     * Extracts item entries from the provided SNBT loot table definition.
     *
     * @param snbt SNBT loot table text
     * @return list of loot table items including their inferred default counts
     */
    public List<LootTableItem> parseItems(String snbt) {
        return parse(snbt).items();
    }

    private List<LootTableItem> parseItemsArray(Object itemsObj) {
        if (!(itemsObj instanceof List<?> items)) {
            return List.of();
        }
        List<LootTableItem> parsed = new ArrayList<>();
        for (Object itemObj : items) {
            if (!(itemObj instanceof Map<?, ?> rawItem)) {
                continue;
            }
            Object idValue = rawItem.get("id");
            if (idValue == null) {
                idValue = rawItem.get("name");
            }
            String itemId = asString(idValue);
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            int count = parseNumber(rawItem.get("count"), 1);
            int weight = parseNumber(rawItem.get("weight"), DEFAULT_WEIGHT);
            String displayName = formatDisplayName(itemId);
            parsed.add(new LootTableItem(itemId, displayName, count, weight));
        }
        return Collections.unmodifiableList(parsed);
    }

    private List<LootTableItem> parseLegacyPools(Map<String, Object> root) {
        Object poolsObj = root.get("pools");
        if (!(poolsObj instanceof List<?> pools)) {
            return List.of();
        }
        List<LootTableItem> items = new ArrayList<>();
        for (Object poolObj : pools) {
            if (!(poolObj instanceof Map<?, ?> pool)) {
                continue;
            }
            Object entriesObj = pool.get("entries");
            if (!(entriesObj instanceof List<?> entries)) {
                continue;
            }
            for (Object entryObj : entries) {
                parseLegacyEntry(entryObj).ifPresent(items::add);
            }
        }
        return Collections.unmodifiableList(items);
    }

    private Optional<LootTableItem> parseLegacyEntry(Object entryObj) {
        if (!(entryObj instanceof Map<?, ?> entry)) {
            return Optional.empty();
        }
        String type = asString(entry.get("type"));
        if (type != null && !type.toLowerCase(Locale.ROOT).contains("item")) {
            return Optional.empty();
        }
        String itemId = asString(entry.get("name"));
        if (itemId == null || itemId.isBlank()) {
            return Optional.empty();
        }
        int count = extractCount(entry);
        int weight = extractWeight(entry);
        String displayName = formatDisplayName(itemId);
        return Optional.of(new LootTableItem(itemId, displayName, count, weight));
    }

    private int extractCount(Map<?, ?> entry) {
        Object directCount = entry.get("count");
        if (directCount != null) {
            int parsed = parseNumber(directCount, 1);
            if (parsed > 0) {
                return parsed;
            }
        }
        Object functionsObj = entry.get("functions");
        if (functionsObj instanceof List<?> functions) {
            for (Object funcObj : functions) {
                if (!(funcObj instanceof Map<?, ?> functionMap)) {
                    continue;
                }
                String functionId = asString(functionMap.get("function"));
                if (functionId == null) {
                    continue;
                }
                String lower = functionId.toLowerCase(Locale.ROOT);
                if (!lower.endsWith("set_count") && !lower.contains(":set_count")) {
                    continue;
                }
                Object countObj = functionMap.get("count");
                int parsed = parseNumber(countObj, 0);
                if (parsed > 0) {
                    return parsed;
                }
            }
        }
        return 1;
    }

    private int extractWeight(Map<?, ?> entry) {
        Object weight = entry.get("weight");
        int parsed = parseNumber(weight, DEFAULT_WEIGHT);
        return parsed > 0 ? parsed : DEFAULT_WEIGHT;
    }

    private int parseNumber(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        if (value instanceof String string) {
            try {
                return Math.max(1, Integer.parseInt(string.trim()));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        if (value instanceof Map<?, ?> map) {
            Object direct = map.get("value");
            if (direct != null) {
                return parseNumber(direct, defaultValue);
            }
        }
        return defaultValue;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        return null;
    }

    private String formatDisplayName(String itemId) {
        int colon = itemId.indexOf(':');
        String base = colon >= 0 ? itemId.substring(colon + 1) : itemId;
        String[] parts = base.split("[_ ]");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        String formatted = builder.toString();
        if (formatted.isBlank()) {
            formatted = itemId;
        }
        return formatted;
    }

    public record LootTableData(String icon, List<LootTableItem> items) {
        public LootTableData {
            Objects.requireNonNull(icon, "icon");
            items = List.copyOf(Objects.requireNonNull(items, "items"));
        }
    }

    public record LootTableItem(String itemId, String displayName, int defaultCount, int weight) {
        public LootTableItem {
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(displayName, "displayName");
            defaultCount = Math.max(1, defaultCount);
            weight = Math.max(1, weight);
        }
    }
}
