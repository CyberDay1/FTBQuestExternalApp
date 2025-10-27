package dev.ftbq.editor.ingest;

import dev.ftbq.editor.resources.ResourceId;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/**
 * Caches item icons stored on disk and loads them lazily on demand.
 */
public final class IconCache {

    private final Path iconsDirectory;
    private final Map<ResourceId, String> iconIndex = new ConcurrentHashMap<>();
    private final Map<ResourceId, Image> images = new ConcurrentHashMap<>();

    public IconCache(Path cacheRoot, Iterable<ItemMeta> items) {
        Objects.requireNonNull(cacheRoot, "cacheRoot");
        Objects.requireNonNull(items, "items");
        this.iconsDirectory = cacheRoot.resolve("icons");
        try {
            Files.createDirectories(this.iconsDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create icon cache directory", e);
        }
        for (ItemMeta item : items) {
            if (item == null) {
                continue;
            }
            String hash = item.iconHash();
            if (hash == null || hash.isBlank()) {
                continue;
            }
            iconIndex.put(ResourceId.fromString(item.id()), hash);
        }
    }

    public void register(ResourceId id, String iconHash) {
        Objects.requireNonNull(id, "id");
        if (iconHash == null || iconHash.isBlank()) {
            return;
        }
        iconIndex.put(id, iconHash);
    }

    public Image getIcon(ResourceId id) {
        Objects.requireNonNull(id, "id");
        return images.computeIfAbsent(id, this::loadIcon);
    }

    private Image loadIcon(ResourceId id) {
        String reference = iconIndex.getOrDefault(id, id.toString());
        if (reference == null || reference.isBlank()) {
            return null;
        }
        Path iconPath = resolveIconPath(reference);
        if (iconPath == null || !Files.exists(iconPath)) {
            return null;
        }
        try (InputStream input = Files.newInputStream(iconPath)) {
            BufferedImage image = ImageIO.read(input);
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load icon for " + id, e);
        }
    }

    private Path resolveIconPath(String reference) {
        if (reference.indexOf(':') >= 0) {
            try {
                ResourceId iconId = ResourceId.fromString(reference);
                Path namespaceDirectory = iconsDirectory.resolve(iconId.namespace());
                return namespaceDirectory.resolve(iconId.path() + ".png");
            } catch (IllegalArgumentException ignored) {
                // Fall through to legacy resolution for malformed identifiers.
            }
        }
        return iconsDirectory.resolve(reference + ".png");
    }
}

