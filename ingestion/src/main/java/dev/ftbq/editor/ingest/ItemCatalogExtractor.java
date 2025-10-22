package dev.ftbq.editor.ingest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Utilities for extracting {@link ItemCatalog} instances from scanned JAR files.
 */
public final class ItemCatalogExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() { };

    private ItemCatalogExtractor() {
        throw new AssertionError("ItemCatalogExtractor cannot be instantiated");
    }

    /**
     * Extract an {@link ItemCatalog} from the supplied JAR file.
     *
     * @param jar       source JAR path
     * @param source    identifier for the catalog (usually the file name)
     * @param version   version string associated with the catalog
     * @param isVanilla whether the catalog represents vanilla Minecraft assets
     * @return populated {@link ItemCatalog}
     * @throws IOException if the archive cannot be read
     */
    public static ItemCatalog extract(Path jar, String source, String version, boolean isVanilla) throws IOException {
        Objects.requireNonNull(jar, "jar");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(version, "version");

        Map<String, ItemMeta> items = new LinkedHashMap<>();
        Map<String, Set<String>> tags = new TreeMap<>();

        try (ZipFile zipFile = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                if (name.startsWith("assets/") && name.endsWith("/lang/en_us.json")) {
                    String namespace = extractNamespace(name, "assets/");
                    if (namespace.isEmpty()) {
                        continue;
                    }
                    try (InputStream input = zipFile.getInputStream(entry)) {
                        Map<String, String> translations = MAPPER.readValue(input, STRING_MAP);
                        translations.forEach((key, value) ->
                                parseLangEntry(namespace, key, value, isVanilla).ifPresent(meta ->
                                        items.putIfAbsent(meta.id(), meta)));
                    }
                } else if (name.startsWith("data/") && name.contains("/tags/items/") && name.endsWith(".json")) {
                    String namespace = extractNamespace(name, "data/");
                    if (namespace.isEmpty()) {
                        continue;
                    }
                    String tagId = buildTagId(namespace, name);
                    if (tagId == null) {
                        continue;
                    }
                    try (InputStream input = zipFile.getInputStream(entry)) {
                        JsonNode root = MAPPER.readTree(input);
                        JsonNode valuesNode = root.get("values");
                        if (valuesNode != null && valuesNode.isArray()) {
                            Set<String> collected = tags.computeIfAbsent(tagId, key -> new LinkedHashSet<>());
                            valuesNode.forEach(node -> {
                                if (!node.isTextual()) {
                                    return;
                                }
                                String value = node.asText();
                                if (value.startsWith("#")) {
                                    return;
                                }
                                collected.add(value);
                            });
                        }
                    }
                }
            }
        }

        // Ensure all items referenced by tags exist in the item list.
        for (Set<String> values : tags.values()) {
            for (String value : values) {
                items.computeIfAbsent(value, id -> createPlaceholderItem(id, isVanilla));
            }
        }

        List<ItemMeta> sortedItems = new ArrayList<>(items.values());
        sortedItems.sort(Comparator.comparing(ItemMeta::id));

        Map<String, List<String>> finalizedTags = new LinkedHashMap<>();
        tags.forEach((tag, values) -> {
            List<String> sortedValues = new ArrayList<>(values);
            Collections.sort(sortedValues);
            finalizedTags.put(tag, Collections.unmodifiableList(sortedValues));
        });

        return new ItemCatalog(source, version, isVanilla, Collections.unmodifiableList(sortedItems), finalizedTags);
    }

    private static String extractNamespace(String path, String prefix) {
        int start = prefix.length();
        int slash = path.indexOf('/', start);
        if (slash < 0) {
            return "";
        }
        return path.substring(start, slash);
    }

    private static Optional<ItemMeta> parseLangEntry(String namespaceFromPath, String key, String value, boolean isVanilla) {
        if (key == null || value == null) {
            return Optional.empty();
        }
        String[] parts = key.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        String kind = parts[0];
        if (!kind.equals("item") && !kind.equals("block")) {
            return Optional.empty();
        }
        String namespace = parts[1];
        if (!namespace.equals(namespaceFromPath)) {
            return Optional.empty();
        }
        String itemName = parts[2];
        if (itemName.isEmpty()) {
            return Optional.empty();
        }
        String id = namespace + ":" + itemName;
        return Optional.of(new ItemMeta(
                id,
                value,
                namespace,
                kind,
                isVanilla,
                null,
                null,
                namespace,
                null
        ));
    }

    private static ItemMeta createPlaceholderItem(String id, boolean isVanilla) {
        int colon = id.indexOf(':');
        String namespace = colon > 0 ? id.substring(0, colon) : "minecraft";
        String itemName = colon > 0 ? id.substring(colon + 1) : id;
        return new ItemMeta(
                namespace + ":" + itemName,
                id,
                namespace,
                "item",
                isVanilla,
                null,
                null,
                namespace,
                null
        );
    }

    private static String buildTagId(String namespace, String path) {
        int start = path.indexOf("/tags/items/");
        if (start < 0) {
            return null;
        }
        start += "/tags/items/".length();
        String remainder = path.substring(start);
        if (!remainder.endsWith(".json")) {
            return null;
        }
        String tagPath = remainder.substring(0, remainder.length() - 5);
        if (tagPath.isEmpty()) {
            return null;
        }
        return namespace + ":" + tagPath;
    }
}
