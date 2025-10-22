package dev.ftbq.editor.domain.version;

import java.util.List;

/**
 * Defines access to vanilla and modded item catalogs per Minecraft version.
 */
public interface VersionCatalog {
    MinecraftVersion getActiveVersion();

    void setActiveVersion(MinecraftVersion version);

    ItemCatalog getVanillaItems();

    ItemCatalog mergeWithMods(List<ItemCatalog> modCatalogs);
}
