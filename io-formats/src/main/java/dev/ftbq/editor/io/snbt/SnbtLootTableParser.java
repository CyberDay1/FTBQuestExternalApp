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

    /**
     * Extracts item entries from the provided SNBT loot table definition.
     *
     * @param snbt SNBT loot table text
     * @return list of loot table items including their inferred default counts
     */
    public List<LootTableItem> parseItems(String snbt) {
        if (snbt == null || snbt.isBlank()) {
            return List.of();
        }
        Map<String, Object> root;
        try {
            root = SnbtParser.parseRootCompound(snbt);
        } catch (SnbtParseException | IllegalArgumentException ex) {
            return List.of();
        }
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
                parseEntry(entryObj).ifPresent(items::add);
            }
        }
        return Collections.unmodifiableList(items);
    }

    private Optional<LootTableItem> parseEntry(Object entryObj) {
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
        String displayName = formatDisplayName(itemId);
        return Optional.of(new LootTableItem(itemId, displayName, count));
    }

    private int extractCount(Map<?, ?> entry) {
        Object directCount = entry.get("count");
        if (directCount instanceof Number number) {
            return Math.max(1, number.intValue());
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
                int parsed = parseCountValue(countObj);
                if (parsed > 0) {
                    return parsed;
                }
            }
        }
        return 1;
    }

    private int parseCountValue(Object countObj) {
        if (countObj instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        if (countObj instanceof Map<?, ?> countMap) {
            Object max = countMap.get("max");
            if (max instanceof Number number) {
                return Math.max(1, number.intValue());
            }
            Object min = countMap.get("min");
            if (min instanceof Number number) {
                return Math.max(1, number.intValue());
            }
        }
        return 0;
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

    public record LootTableItem(String itemId, String displayName, int defaultCount) {
        public LootTableItem {
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(displayName, "displayName");
            defaultCount = Math.max(1, defaultCount);
        }
    }
}
