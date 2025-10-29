package dev.ftbq.editor.io.snbt;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Produces SNBT language files following the FTB Quests key conventions.
 */
public final class SnbtLangBuilder {

    public String buildEnUs(QuestFile questFile) {
        Objects.requireNonNull(questFile, "questFile");
        List<LangEntry> entries = collectEntries(questFile);
        return build(entries, true);
    }

    public String buildStub(QuestFile questFile, boolean copyValues) {
        Objects.requireNonNull(questFile, "questFile");
        List<LangEntry> entries = collectEntries(questFile);
        return build(entries, copyValues);
    }

    private List<LangEntry> collectEntries(QuestFile questFile) {
        Map<String, LangEntry> entries = new LinkedHashMap<>();
        for (Chapter chapter : questFile.chapters()) {
            String chapterKey = "chapter." + chapter.id() + ".title";
            entries.put(chapterKey, LangEntry.single(chapterKey, chapter.title()));
            for (Quest quest : chapter.quests()) {
                String questTitleKey = "quest." + quest.id() + ".title";
                entries.put(questTitleKey, LangEntry.single(questTitleKey, quest.title()));
                List<String> descriptionLines = splitDescription(quest.description());
                String descriptionKey = "quest." + quest.id() + ".quest_desc";
                entries.put(descriptionKey, LangEntry.list(descriptionKey, descriptionLines));
            }
        }
        return new ArrayList<>(entries.values());
    }

    private List<String> splitDescription(String description) {
        List<String> lines = new ArrayList<>();
        if (description == null || description.isEmpty()) {
            return lines;
        }
        String[] rawLines = description.split("\\r?\\n", -1);
        for (String raw : rawLines) {
            lines.add(raw);
        }
        return lines;
    }

    private String build(List<LangEntry> entries, boolean copyValues) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        for (int i = 0; i < entries.size(); i++) {
            LangEntry entry = entries.get(i);
            builder.append("  ").append(formatKey(entry.key())).append(":");
            if (entry.isList()) {
                List<String> values = copyValues ? entry.values() : List.of();
                builder.append("[");
                for (int j = 0; j < values.size(); j++) {
                    builder.append('"').append(escape(values.get(j))).append('"');
                    if (j < values.size() - 1) {
                        builder.append(", ");
                    }
                }
                builder.append(']');
            } else {
                String value = copyValues && !entry.values().isEmpty() ? entry.values().get(0) : "";
                builder.append('"').append(escape(value)).append('"');
            }
            if (i < entries.size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append('}');
        return builder.toString();
    }

    private String formatKey(String key) {
        boolean needsQuotes = key.isEmpty();
        for (int i = 0; i < key.length() && !needsQuotes; i++) {
            char c = key.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '.' )) {
                needsQuotes = true;
            }
        }
        if (!needsQuotes) {
            return key;
        }
        return '"' + escape(key) + '"';
    }

    private String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    private record LangEntry(String key, List<String> values, boolean isList) {
        static LangEntry single(String key, String value) {
            return new LangEntry(key, List.of(value == null ? "" : value), false);
        }

        static LangEntry list(String key, List<String> values) {
            return new LangEntry(key, List.copyOf(values), true);
        }
    }
}
