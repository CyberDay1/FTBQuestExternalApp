package dev.ftbq.editor.services.version;

import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.version.ItemCatalog;
import dev.ftbq.editor.domain.version.MinecraftVersion;
import dev.ftbq.editor.domain.version.VersionCatalog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default in-memory implementation of {@link VersionCatalog}.
 */
public class VersionCatalogImpl implements VersionCatalog {
    private final Map<MinecraftVersion, ItemCatalog> vanillaCatalogs;
    private MinecraftVersion activeVersion;

    public VersionCatalogImpl(Map<MinecraftVersion, ItemCatalog> vanillaCatalogs, MinecraftVersion defaultVersion) {
        Objects.requireNonNull(vanillaCatalogs, "vanillaCatalogs");
        if (vanillaCatalogs.isEmpty()) {
            throw new IllegalArgumentException("At least one vanilla catalog must be provided");
        }

        EnumMap<MinecraftVersion, ItemCatalog> copy = new EnumMap<>(MinecraftVersion.class);
        copy.putAll(vanillaCatalogs);
        this.vanillaCatalogs = Collections.unmodifiableMap(copy);

        MinecraftVersion versionToUse = defaultVersion;
        if (versionToUse == null) {
            versionToUse = copy.keySet().iterator().next();
        }

        if (!this.vanillaCatalogs.containsKey(versionToUse)) {
            throw new IllegalArgumentException("Default version does not have a vanilla catalog: " + versionToUse);
        }
        this.activeVersion = versionToUse;
    }

    public VersionCatalogImpl(Map<MinecraftVersion, ItemCatalog> vanillaCatalogs) {
        this(vanillaCatalogs, null);
    }

    @Override
    public MinecraftVersion getActiveVersion() {
        return activeVersion;
    }

    @Override
    public void setActiveVersion(MinecraftVersion version) {
        Objects.requireNonNull(version, "version");
        if (!vanillaCatalogs.containsKey(version)) {
            throw new IllegalArgumentException("No vanilla catalog available for version " + version);
        }
        activeVersion = version;
    }

    @Override
    public ItemCatalog getVanillaItems() {
        ItemCatalog catalog = vanillaCatalogs.get(activeVersion);
        if (catalog == null) {
            throw new IllegalStateException("No vanilla catalog configured for active version " + activeVersion);
        }
        return catalog;
    }

    @Override
    public ItemCatalog mergeWithMods(List<ItemCatalog> modCatalogs) {
        List<ItemCatalog> catalogsToMerge = new ArrayList<>();
        catalogsToMerge.add(getVanillaItems());
        if (modCatalogs != null) {
            for (ItemCatalog catalog : modCatalogs) {
                if (catalog != null) {
                    catalogsToMerge.add(catalog);
                }
            }
        }

        if (catalogsToMerge.size() == 1) {
            return catalogsToMerge.get(0);
        }

        return new CombinedItemCatalog(catalogsToMerge);
    }

    private static final class CombinedItemCatalog implements ItemCatalog {
        private final List<ItemRef> items;

        private CombinedItemCatalog(List<ItemCatalog> catalogs) {
            Map<String, ItemRef> merged = new LinkedHashMap<>();
            for (ItemCatalog catalog : catalogs) {
                Collection<ItemRef> sourceItems = catalog.items();
                if (sourceItems == null) {
                    continue;
                }
                for (ItemRef item : sourceItems) {
                    if (item != null) {
                        merged.put(item.itemId(), item);
                    }
                }
            }
            this.items = List.copyOf(merged.values());
        }

        @Override
        public Collection<ItemRef> items() {
            return items;
        }
    }
}
