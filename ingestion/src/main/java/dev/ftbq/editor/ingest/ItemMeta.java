package dev.ftbq.editor.ingest;

import java.util.Objects;

/**
 * Metadata describing a single item or block reference extracted from a content pack.
 */
public record ItemMeta(
        String id,
        String displayName,
        String namespace,
        String kind,
        boolean isVanilla,
        String texturePath,
        String iconHash,
        String modId,
        String modName,
        String modVersion
) {
    public ItemMeta {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(kind, "kind");
    }
}
