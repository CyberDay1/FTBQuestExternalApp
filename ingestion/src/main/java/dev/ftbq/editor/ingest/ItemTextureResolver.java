package dev.ftbq.editor.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ftbq.editor.resources.ResourceId;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Resolves item textures by following model indirections when necessary.
 */
public final class ItemTextureResolver {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> TEXTURE_PRIORITY = List.of("layer0", "texture", "all", "particle");

    private ItemTextureResolver() {
        throw new AssertionError("ItemTextureResolver cannot be instantiated");
    }

    public static Optional<TextureLocation> findTexture(Map<String, File> jars, String namespace, String itemPath) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(itemPath, "itemPath");
        ResourceId itemId = new ResourceId(namespace, itemPath);
        return resolveTexture(jars, itemId);
    }

    public static Optional<TextureLocation> resolveTexture(Map<String, File> jars, ResourceId itemId) {
        if (jars == null || itemId == null) {
            return Optional.empty();
        }

        try (ZipFileProvider provider = new ZipFileProvider(jars)) {
            Optional<TextureLocation> direct = locateDirectTexture(provider, itemId);
            if (direct.isPresent()) {
                return direct;
            }

            ResourceId modelId = new ResourceId(itemId.namespace(), "item/" + itemId.path());
            Optional<TextureLocation> viaModel = resolveModelTexture(provider, modelId, new HashSet<>());
            if (viaModel.isPresent()) {
                return viaModel;
            }

            Optional<TextureLocation> defaultTexture = locateDefaultTexture(provider);
            if (defaultTexture.isPresent()) {
                return defaultTexture;
            }
        } catch (IOException ignored) {
            // Ignore and fall through to the empty result.
        }

        return Optional.empty();
    }

    private static Optional<TextureLocation> locateDirectTexture(ZipFileProvider provider, ResourceId itemId)
            throws IOException {
        ResourceId textureId = new ResourceId(itemId.namespace(), "item/" + itemId.path());
        return locateTextureResource(provider, textureId);
    }

    private static Optional<TextureLocation> resolveModelTexture(ZipFileProvider provider, ResourceId modelId,
            Set<ResourceId> visited) throws IOException {
        if (!visited.add(modelId)) {
            return Optional.empty();
        }

        ZipFile zipFile = provider.open(modelId.namespace());
        if (zipFile == null) {
            return Optional.empty();
        }

        String entryName = "assets/" + modelId.namespace() + "/models/" + modelId.path() + ".json";
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            return Optional.empty();
        }

        try (InputStream input = zipFile.getInputStream(entry)) {
            JsonNode root = MAPPER.readTree(input);
            if (root == null) {
                return Optional.empty();
            }

            JsonNode texturesNode = root.get("textures");
            if (texturesNode != null && texturesNode.isObject()) {
                Map<String, String> textureMap = collectTextures(texturesNode);
                List<String> ordered = new ArrayList<>();
                for (String key : TEXTURE_PRIORITY) {
                    String value = textureMap.get(key);
                    if (value != null) {
                        ordered.add(value);
                    }
                }
                for (String value : textureMap.values()) {
                    if (!ordered.contains(value)) {
                        ordered.add(value);
                    }
                }

                for (String candidate : ordered) {
                    String resolved = resolveAlias(candidate, textureMap, new HashSet<>());
                    if (resolved == null || resolved.isBlank()) {
                        continue;
                    }
                    ResourceId textureId = ResourceId.fromString(resolved, modelId.namespace());
                    Optional<TextureLocation> resolvedTexture = locateTextureResource(provider, textureId);
                    if (resolvedTexture.isPresent()) {
                        return resolvedTexture;
                    }
                }
            }

            JsonNode parentNode = root.get("parent");
            if (parentNode != null && parentNode.isTextual()) {
                String parentRef = parentNode.asText();
                if (!parentRef.isBlank()) {
                    ResourceId parentId = ResourceId.fromString(parentRef, modelId.namespace());
                    // Parent references include the directory (item/, block/, etc.).
                    Optional<TextureLocation> resolvedParent = resolveModelTexture(provider, parentId, visited);
                    if (resolvedParent.isPresent()) {
                        return resolvedParent;
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static Map<String, String> collectTextures(JsonNode texturesNode) {
        Map<String, String> textureMap = new LinkedHashMap<>();
        texturesNode.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value != null && value.isTextual()) {
                textureMap.put(entry.getKey(), value.asText());
            }
        });
        return textureMap;
    }

    private static String resolveAlias(String value, Map<String, String> textureMap, Set<String> visitedKeys) {
        if (value == null || !value.startsWith("#")) {
            return value;
        }
        String key = value.substring(1);
        if (!visitedKeys.add(key)) {
            return null;
        }
        String next = textureMap.get(key);
        if (next == null) {
            return null;
        }
        return resolveAlias(next, textureMap, visitedKeys);
    }

    private static Optional<TextureLocation> locateTextureResource(ZipFileProvider provider, ResourceId textureId)
            throws IOException {
        ZipFile zipFile = provider.open(textureId.namespace());
        if (zipFile == null) {
            return Optional.empty();
        }
        String entryName = "assets/" + textureId.namespace() + "/textures/" + textureId.path() + ".png";
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            return Optional.empty();
        }
        File jarFile = provider.jarFile(textureId.namespace());
        return Optional.of(new TextureLocation(jarFile, textureId, entryName));
    }

    private static Optional<TextureLocation> locateDefaultTexture(ZipFileProvider provider) throws IOException {
        ResourceId defaultId = new ResourceId("minecraft", "item/default");
        return locateTextureResource(provider, defaultId);
    }

    /**
     * Describes the resolved texture and its source.
     */
    public record TextureLocation(File jar, ResourceId textureId, String entryName) { }

    private static final class ZipFileProvider implements AutoCloseable {
        private final Map<String, File> jarsByNamespace;
        private final Map<String, ZipFile> openArchives = new HashMap<>();

        private ZipFileProvider(Map<String, File> jars) {
            this.jarsByNamespace = jars != null ? new HashMap<>(jars) : Map.of();
        }

        ZipFile open(String namespace) throws IOException {
            File jarFile = jarsByNamespace.get(namespace);
            if (jarFile == null || !jarFile.isFile()) {
                return null;
            }
            ZipFile existing = openArchives.get(namespace);
            if (existing != null) {
                return existing;
            }
            ZipFile zipFile = new ZipFile(jarFile);
            openArchives.put(namespace, zipFile);
            return zipFile;
        }

        File jarFile(String namespace) {
            return jarsByNamespace.get(namespace);
        }

        @Override
        public void close() throws IOException {
            IOException first = null;
            for (ZipFile zipFile : openArchives.values()) {
                try {
                    zipFile.close();
                } catch (IOException ex) {
                    if (first == null) {
                        first = ex;
                    }
                }
            }
            openArchives.clear();
            if (first != null) {
                throw first;
            }
        }
    }
}
