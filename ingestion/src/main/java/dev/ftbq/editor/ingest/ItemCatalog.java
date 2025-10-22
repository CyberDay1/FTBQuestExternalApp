package dev.ftbq.editor.ingest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Catalog of items extracted from a single source (vanilla version or mod JAR).
 */
public record ItemCatalog(
        String source,
        String version,
        boolean isVanilla,
        List<ItemMeta> items,
        Map<String, List<String>> tags
) {
    public ItemCatalog {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(tags, "tags");

        items = List.copyOf(items);

        Map<String, List<String>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : tags.entrySet()) {
            String tag = Objects.requireNonNull(entry.getKey(), "tag key");
            List<String> values = Objects.requireNonNull(entry.getValue(), "tag values");
            normalized.put(tag, List.copyOf(values));
        }
        tags = Collections.unmodifiableMap(normalized);
    }
}
