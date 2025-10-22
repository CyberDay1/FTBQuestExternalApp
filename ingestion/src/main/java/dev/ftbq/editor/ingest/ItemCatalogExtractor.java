package dev.ftbq.editor.ingest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ftbq.editor.resources.ResourceId;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
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
        Map<ResourceId, ModelDefinition> models = new LinkedHashMap<>();

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
                } else if (name.startsWith("assets/") && name.contains("/models/item/") && name.endsWith(".json")) {
                    parseModelEntry(zipFile, entry, models);
                }
            }

            for (Set<String> values : tags.values()) {
                for (String value : values) {
                    items.computeIfAbsent(value, id -> createPlaceholderItem(id, isVanilla));
                }
            }

            Path iconCacheDirectory = ensureIconCacheDirectory();
            Map<ResourceId, Map<String, String>> mergedTextureCache = new HashMap<>();
            List<ItemMeta> sortedItems = new ArrayList<>(items.size());
            for (ItemMeta meta : items.values()) {
                sortedItems.add(enrichWithIcon(meta, models, mergedTextureCache, zipFile, iconCacheDirectory));
            }
            sortedItems.sort(Comparator.comparing(ItemMeta::id));

            Map<String, List<String>> finalizedTags = finalizeTags(tags);

            return new ItemCatalog(source, version, isVanilla, Collections.unmodifiableList(sortedItems), finalizedTags);
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

    private static Path ensureIconCacheDirectory() throws IOException {
        Path directory = Path.of("cache", "icons");
        Files.createDirectories(directory);
        return directory;
    }

    private static void parseModelEntry(ZipFile zipFile, ZipEntry entry, Map<ResourceId, ModelDefinition> models) throws IOException {
        String namespace = extractNamespace(entry.getName(), "assets/");
        if (namespace.isEmpty()) {
            return;
        }
        int modelsIndex = entry.getName().indexOf("/models/");
        if (modelsIndex < 0) {
            return;
        }
        int relativeStart = modelsIndex + "/models/".length();
        String relativePath = entry.getName().substring(relativeStart, entry.getName().length() - ".json".length());
        ResourceId modelId = new ResourceId(namespace, relativePath);
        try (InputStream input = zipFile.getInputStream(entry)) {
            JsonNode root = MAPPER.readTree(input);
            ResourceId parent = null;
            JsonNode parentNode = root.get("parent");
            if (parentNode != null && parentNode.isTextual()) {
                String value = parentNode.asText();
                if (!value.isBlank()) {
                    parent = ResourceId.fromString(value, namespace);
                }
            }
            Map<String, String> textures = new LinkedHashMap<>();
            JsonNode texturesNode = root.get("textures");
            if (texturesNode != null && texturesNode.isObject()) {
                texturesNode.fields().forEachRemaining(field -> {
                    JsonNode value = field.getValue();
                    if (value != null && value.isTextual()) {
                        textures.put(field.getKey(), value.asText());
                    }
                });
            }
            models.put(modelId, new ModelDefinition(modelId, parent, Collections.unmodifiableMap(textures)));
        }
    }

    private static ItemMeta enrichWithIcon(
            ItemMeta meta,
            Map<ResourceId, ModelDefinition> models,
            Map<ResourceId, Map<String, String>> mergedTextureCache,
            ZipFile zipFile,
            Path iconCacheDirectory
    ) throws IOException {
        ResourceId itemId = ResourceId.fromString(meta.id(), meta.namespace());
        TextureResolution texture = resolveItemTexture(itemId, models, mergedTextureCache, zipFile, iconCacheDirectory);
        if (texture == null) {
            return meta;
        }
        return new ItemMeta(
                meta.id(),
                meta.displayName(),
                meta.namespace(),
                meta.kind(),
                meta.isVanilla(),
                texture.texturePath(),
                texture.iconHash(),
                meta.modId(),
                meta.modName()
        );
    }

    private static TextureResolution resolveItemTexture(
            ResourceId itemId,
            Map<ResourceId, ModelDefinition> models,
            Map<ResourceId, Map<String, String>> mergedTextureCache,
            ZipFile zipFile,
            Path iconCacheDirectory
    ) throws IOException {
        ResourceId modelId = findModelId(itemId, models);
        if (modelId == null) {
            return null;
        }
        ResourceId textureId = resolveTextureForModel(modelId, models, mergedTextureCache);
        if (textureId == null) {
            return null;
        }
        String iconHash = storeTexture(textureId, zipFile, iconCacheDirectory);
        return new TextureResolution(textureId.toString(), iconHash);
    }

    private static ResourceId findModelId(ResourceId itemId, Map<ResourceId, ModelDefinition> models) {
        ResourceId primary = itemId.withPath("item/" + itemId.path());
        if (models.containsKey(primary)) {
            return primary;
        }
        if (models.containsKey(itemId)) {
            return itemId;
        }
        return null;
    }

    private static ResourceId resolveTextureForModel(
            ResourceId modelId,
            Map<ResourceId, ModelDefinition> models,
            Map<ResourceId, Map<String, String>> mergedTextureCache
    ) {
        Map<String, String> textures = resolveMergedTextures(modelId, models, mergedTextureCache, new HashSet<>());
        if (textures.isEmpty()) {
            return null;
        }
        for (String key : TEXTURE_PREFERENCE) {
            ResourceId resolved = resolveTextureReference(modelId, textures.get(key), models, mergedTextureCache, new HashSet<>());
            if (resolved != null) {
                return resolved;
            }
        }
        for (String value : textures.values()) {
            ResourceId resolved = resolveTextureReference(modelId, value, models, mergedTextureCache, new HashSet<>());
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private static Map<String, String> resolveMergedTextures(
            ResourceId modelId,
            Map<ResourceId, ModelDefinition> models,
            Map<ResourceId, Map<String, String>> cache,
            Set<ResourceId> visiting
    ) {
        Map<String, String> cached = cache.get(modelId);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(modelId)) {
            return Collections.emptyMap();
        }
        ModelDefinition definition = models.get(modelId);
        if (definition == null) {
            visiting.remove(modelId);
            cache.put(modelId, Collections.emptyMap());
            return Collections.emptyMap();
        }
        Map<String, String> merged = new LinkedHashMap<>();
        if (definition.parent() != null) {
            merged.putAll(resolveMergedTextures(definition.parent(), models, cache, visiting));
        }
        merged.putAll(definition.textures());
        visiting.remove(modelId);
        Map<String, String> result = Collections.unmodifiableMap(merged);
        cache.put(modelId, result);
        return result;
    }

    private static ResourceId resolveTextureReference(
            ResourceId modelId,
            String reference,
            Map<ResourceId, ModelDefinition> models,
            Map<ResourceId, Map<String, String>> mergedTextureCache,
            Set<String> seenKeys
    ) {
        if (reference == null || reference.isEmpty()) {
            return null;
        }
        if (reference.startsWith("#")) {
            String key = reference.substring(1);
            return resolveTextureKey(modelId, key, models, mergedTextureCache, seenKeys);
        }
        return ResourceId.fromString(reference, modelId.namespace());
    }

    private static ResourceId resolveTextureKey(
            ResourceId modelId,
            String key,
            Map<ResourceId, ModelDefinition> models,
            Map<ResourceId, Map<String, String>> mergedTextureCache,
            Set<String> seenKeys
    ) {
        String marker = modelId + "#" + key;
        if (!seenKeys.add(marker)) {
            return null;
        }
        Map<String, String> textures = resolveMergedTextures(modelId, models, mergedTextureCache, new HashSet<>());
        String value = textures.get(key);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return resolveTextureReference(modelId, value, models, mergedTextureCache, seenKeys);
    }

    private static String storeTexture(ResourceId textureId, ZipFile zipFile, Path iconCacheDirectory) throws IOException {
        String entryName = buildTextureEntryName(textureId);
        ZipEntry textureEntry = zipFile.getEntry(entryName);
        if (textureEntry == null) {
            return null;
        }
        try (InputStream input = zipFile.getInputStream(textureEntry)) {
            byte[] bytes = input.readAllBytes();
            String hash = hashBytes(bytes);
            Path output = iconCacheDirectory.resolve(hash + ".png");
            if (!Files.exists(output)) {
                Files.write(output, bytes);
            }
            return hash;
        }
    }

    private static String buildTextureEntryName(ResourceId textureId) {
        String path = textureId.path();
        if (!path.endsWith(".png")) {
            path = path + ".png";
        }
        return "assets/" + textureId.namespace() + "/textures/" + path;
    }

    private static String hashBytes(byte[] bytes) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
        byte[] hashed = digest.digest(bytes);
        StringBuilder builder = new StringBuilder(hashed.length * 2);
        for (byte b : hashed) {
            int value = b & 0xFF;
            if (value < 0x10) {
                builder.append('0');
            }
            builder.append(Integer.toHexString(value));
        }
        return builder.toString();
    }

    private record ModelDefinition(ResourceId id, ResourceId parent, Map<String, String> textures) { }

    private record TextureResolution(String texturePath, String iconHash) { }
}
