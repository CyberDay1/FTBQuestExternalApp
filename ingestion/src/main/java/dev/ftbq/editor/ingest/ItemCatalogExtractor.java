package dev.ftbq.editor.ingest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ftbq.editor.resources.ResourceId;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private static final List<String> TEXTURE_PREFERENCE = List.of("layer0", "texture", "all", "particle");

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
        Map<String, ModMetadata> modMetadata = new LinkedHashMap<>();
        Map<ResourceId, String> modelTextures = new LinkedHashMap<>();

        try (ZipFile zipFile = new ZipFile(jar.toFile())) {
            modMetadata.putAll(extractModMetadata(zipFile));

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
                                parseLangEntry(namespace, key, value, isVanilla, modMetadata)
                                        .ifPresent(meta -> items.putIfAbsent(meta.id(), meta)));
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
                } else if (name.startsWith("assets/") && name.contains("/models/item/") && name.endsWith(".json")) {
                    String namespace = extractNamespace(name, "assets/");
                    if (namespace.isEmpty()) {
                        continue;
                    }
                    String itemPath = extractModelPath(name, namespace);
                    if (itemPath.isEmpty()) {
                        continue;
                    }
                    try (InputStream input = zipFile.getInputStream(entry)) {
                        JsonNode root = MAPPER.readTree(input);
                        JsonNode texturesNode = root != null ? root.get("textures") : null;
                        String texture = selectTexture(namespace, texturesNode);
                        if (texture != null) {
                            modelTextures.put(new ResourceId(namespace, itemPath), texture);
                        }
                    }
                }
            }

            // Ensure all items referenced by tags exist in the item list.
            for (Set<String> values : tags.values()) {
                for (String value : values) {
                    items.computeIfAbsent(value, id -> createPlaceholderItem(id, isVanilla, modMetadata));
                }
            }

            List<ItemMeta> baseItems = new ArrayList<>(items.values());
            baseItems.sort(Comparator.comparing(ItemMeta::id));

            Path iconCacheDirectory = ensureIconCacheDirectory();
            List<ItemMeta> enrichedItems = new ArrayList<>(baseItems.size());
            for (ItemMeta meta : baseItems) {
                ResourceId itemId = ResourceId.fromString(meta.id());
                String texturePath = modelTextures.get(itemId);
                String iconHash = null;
                if (texturePath != null) {
                    iconHash = cacheTexture(iconCacheDirectory, texturePath, zipFile);
                }
                enrichedItems.add(new ItemMeta(
                        meta.id(),
                        meta.displayName(),
                        meta.namespace(),
                        meta.kind(),
                        meta.isVanilla(),
                        texturePath,
                        iconHash,
                        meta.modId(),
                        meta.modName(),
                        meta.modVersion()
                ));
            }

            Map<String, List<String>> finalizedTags = finalizeTags(tags);
            return new ItemCatalog(source, version, isVanilla, Collections.unmodifiableList(enrichedItems), finalizedTags);
        }
    }

    private static String extractNamespace(String path, String prefix) {
        int start = prefix.length();
        int slash = path.indexOf('/', start);
        if (slash < 0) {
            return "";
        }
        return path.substring(start, slash);
    }

    private static String extractModelPath(String path, String namespace) {
        String prefix = "assets/" + namespace + "/models/item/";
        if (!path.startsWith(prefix) || !path.endsWith(".json")) {
            return "";
        }
        return path.substring(prefix.length(), path.length() - 5);
    }

    private static String selectTexture(String namespace, JsonNode texturesNode) {
        if (texturesNode == null || !texturesNode.isObject()) {
            return null;
        }
        for (String key : TEXTURE_PREFERENCE) {
            JsonNode value = texturesNode.get(key);
            if (value != null && value.isTextual()) {
                String texture = value.asText();
                if (texture != null && !texture.isBlank()) {
                    return normalizeTexture(namespace, texture);
                }
            }
        }
        var fields = texturesNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode value = entry.getValue();
            if (value != null && value.isTextual()) {
                String texture = value.asText();
                if (texture != null && !texture.isBlank()) {
                    return normalizeTexture(namespace, texture);
                }
            }
        }
        return null;
    }

    private static String normalizeTexture(String namespace, String texture) {
        if (texture.contains(":")) {
            return texture;
        }
        return namespace + ':' + texture;
    }

    private static String cacheTexture(Path iconCacheDirectory, String textureResource, ZipFile zipFile) throws IOException {
        ResourceId textureId = ResourceId.fromString(textureResource);
        String entryPath = "assets/" + textureId.namespace() + "/textures/" + textureId.path() + ".png";
        ZipEntry textureEntry = zipFile.getEntry(entryPath);
        if (textureEntry == null) {
            return null;
        }
        byte[] data;
        try (InputStream input = zipFile.getInputStream(textureEntry)) {
            data = input.readAllBytes();
        }
        String hash = hashBytes(data);
        Path target = iconCacheDirectory.resolve(hash + ".png");
        if (!Files.exists(target)) {
            Files.createDirectories(iconCacheDirectory);
            Files.write(target, data);
        }
        return hash;
    }

    private static String hashBytes(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static Path ensureIconCacheDirectory() throws IOException {
        Path cacheRoot = Path.of("cache");
        Path iconsDir = cacheRoot.resolve("icons");
        Files.createDirectories(iconsDir);
        return iconsDir;
    }

    private static Optional<ItemMeta> parseLangEntry(String namespaceFromPath, String key, String value, boolean isVanilla,
            Map<String, ModMetadata> modMetadata) {
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
        ModMetadata metadata = modMetadata.get(namespace);
        return Optional.of(new ItemMeta(
                id,
                value,
                namespace,
                kind,
                isVanilla,
                null,
                null,
                metadata != null ? metadata.modId() : namespace,
                metadata != null ? metadata.name() : null,
                metadata != null ? metadata.version() : null
        ));
    }

    private static ItemMeta createPlaceholderItem(String id, boolean isVanilla, Map<String, ModMetadata> modMetadata) {
        int colon = id.indexOf(':');
        String namespace = colon > 0 ? id.substring(0, colon) : "minecraft";
        String itemName = colon > 0 ? id.substring(colon + 1) : id;
        ModMetadata metadata = modMetadata.get(namespace);
        return new ItemMeta(
                namespace + ":" + itemName,
                id,
                namespace,
                "item",
                isVanilla,
                null,
                null,
                metadata != null ? metadata.modId() : namespace,
                metadata != null ? metadata.name() : null,
                metadata != null ? metadata.version() : null
        );
    }

    private static Map<String, List<String>> finalizeTags(Map<String, Set<String>> tags) {
        Map<String, List<String>> finalizedTags = new LinkedHashMap<>();
        tags.forEach((tag, values) -> {
            List<String> sortedValues = new ArrayList<>(values);
            Collections.sort(sortedValues);
            finalizedTags.put(tag, Collections.unmodifiableList(sortedValues));
        });
        return Collections.unmodifiableMap(finalizedTags);
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

    private static Map<String, ModMetadata> extractModMetadata(ZipFile zipFile) throws IOException {
        Map<String, ModMetadata> metadata = new LinkedHashMap<>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            if ("META-INF/mods.toml".equals(name)) {
                try (InputStream input = zipFile.getInputStream(entry)) {
                    parseModsToml(input).forEach(mod -> metadata.putIfAbsent(mod.modId(), mod));
                }
            } else if (name.endsWith("fabric.mod.json")) {
                try (InputStream input = zipFile.getInputStream(entry)) {
                    parseFabricModJson(input).forEach(mod -> metadata.putIfAbsent(mod.modId(), mod));
                }
            }
        }
        return metadata;
    }

    private static List<ModMetadata> parseModsToml(InputStream input) throws IOException {
        Map<String, ModMetadata> results = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            boolean inModsSection = false;
            String currentModId = null;
            String currentName = null;
            String currentVersion = null;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.startsWith("[[")) {
                    if (inModsSection && currentModId != null) {
                        results.putIfAbsent(currentModId, new ModMetadata(currentModId, currentName, currentVersion));
                    }
                    inModsSection = "[[mods]]".equals(trimmed);
                    currentModId = null;
                    currentName = null;
                    currentVersion = null;
                    continue;
                }
                if (!inModsSection) {
                    continue;
                }
                int equals = trimmed.indexOf('=');
                if (equals < 0) {
                    continue;
                }
                String key = trimmed.substring(0, equals).trim();
                String value = trimmed.substring(equals + 1).trim();
                value = stripQuotes(value);
                switch (key) {
                    case "modId" -> currentModId = value;
                    case "displayName" -> currentName = value;
                    case "display_name" -> {
                        if (currentName == null || currentName.isBlank()) {
                            currentName = value;
                        }
                    }
                    case "version" -> currentVersion = value;
                    default -> { }
                }
            }
            if (inModsSection && currentModId != null) {
                results.putIfAbsent(currentModId, new ModMetadata(currentModId, currentName, currentVersion));
            }
        }
        return new ArrayList<>(results.values());
    }

    private static List<ModMetadata> parseFabricModJson(InputStream input) throws IOException {
        List<ModMetadata> results = new ArrayList<>();
        JsonNode root = MAPPER.readTree(input);
        if (root == null) {
            return results;
        }
        String id = textOrNull(root.get("id"));
        if (id == null || id.isBlank()) {
            return results;
        }
        String name = textOrNull(root.get("name"));
        String version = textOrNull(root.get("version"));
        ModMetadata base = new ModMetadata(id, name, version);
        results.add(base);
        JsonNode provides = root.get("provides");
        if (provides != null && provides.isArray()) {
            provides.forEach(node -> {
                String alias = textOrNull(node);
                if (alias != null && !alias.isBlank()) {
                    results.add(new ModMetadata(alias, name, version));
                }
            });
        }
        return results;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return null;
    }

    private static String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private record ModMetadata(String modId, String name, String version) { }
}
