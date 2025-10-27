package dev.ftbq.editor.ingest;

import dev.ftbq.editor.resources.ResourceId;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Combines multiple mod JARs into a unified icon index, caching textures locally.
 */
public final class CatalogMergeService {

    private final Map<String, File> jarsByNamespace = new HashMap<>();

    public void addJar(File jar, String namespace) {
        Objects.requireNonNull(jar, "jar");
        Objects.requireNonNull(namespace, "namespace");
        if (!jar.isFile()) {
            return;
        }
        jarsByNamespace.put(namespace.toLowerCase(Locale.ROOT), jar);
    }

    public void registerVanillaJar(File jar) {
        addJar(jar, "minecraft");
    }

    public Map<String, String> buildIconIndex(Map<String, String> items) {
        Map<String, String> iconIndex = new LinkedHashMap<>();
        if (items == null || items.isEmpty()) {
            return iconIndex;
        }

        for (Map.Entry<String, String> entry : items.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            ResourceId itemId;
            try {
                itemId = ResourceId.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            Optional<ItemTextureResolver.TextureLocation> texture =
                    ItemTextureResolver.resolveTexture(jarsByNamespace, itemId);
            String iconReference = texture.map(location -> cacheTexture(itemId, location))
                    .orElseGet(() -> cacheDefaultIcon(itemId));
            if (iconReference != null) {
                iconIndex.put(key, iconReference);
            }
        }

        return iconIndex;
    }

    private String cacheTexture(ResourceId itemId, ItemTextureResolver.TextureLocation location) {
        File jarFile = location.jar();
        if (jarFile == null || !jarFile.isFile()) {
            return cacheDefaultIcon(itemId);
        }
        try (ZipFile zipFile = new ZipFile(jarFile)) {
            ZipEntry entry = zipFile.getEntry(location.entryName());
            if (entry == null) {
                return cacheDefaultIcon(itemId);
            }
            Path target = resolveIconPath(itemId);
            Files.createDirectories(target.getParent());
            try (InputStream input = zipFile.getInputStream(entry)) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return itemId.toString();
        } catch (IOException ex) {
            return cacheDefaultIcon(itemId);
        }
    }

    private String cacheDefaultIcon(ResourceId itemId) {
        try {
            Path target = resolveIconPath(itemId);
            Files.createDirectories(target.getParent());
            if (!Files.exists(target)) {
                Files.write(target, IconAssets.DEFAULT_ICON_BYTES);
            }
            return itemId.toString();
        } catch (IOException ignored) {
            return null;
        }
    }

    private Path resolveIconPath(ResourceId itemId) {
        Path root = Path.of(".cache").resolve("icons");
        return root.resolve(itemId.namespace()).resolve(itemId.path() + ".png");
    }
}
