package dev.ftbq.editor.services.version;

import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.version.ItemCatalog;
import dev.ftbq.editor.domain.version.MinecraftVersion;
import dev.ftbq.editor.domain.version.VersionCatalog;
import dev.ftbq.editor.services.logging.AppLoggerFactory;
import dev.ftbq.editor.services.logging.StructuredLogger;
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
    private final Map<MinecraftVersion, Snapshot> mergedSnapshots;
    private final StructuredLogger logger;
    private MinecraftVersion activeVersion;

    public VersionCatalogImpl(Map<MinecraftVersion, ItemCatalog> vanillaCatalogs, MinecraftVersion defaultVersion) {
        this(vanillaCatalogs, defaultVersion, AppLoggerFactory.create().create(VersionCatalogImpl.class));
    }

    public VersionCatalogImpl(Map<MinecraftVersion, ItemCatalog> vanillaCatalogs) {
        this(vanillaCatalogs, null, AppLoggerFactory.create().create(VersionCatalogImpl.class));
    }

    public VersionCatalogImpl(Map<MinecraftVersion, ItemCatalog> vanillaCatalogs,
                              MinecraftVersion defaultVersion,
                              StructuredLogger logger) {
        Objects.requireNonNull(vanillaCatalogs, "vanillaCatalogs");
        if (vanillaCatalogs.isEmpty()) {
            throw new IllegalArgumentException("At least one vanilla catalog must be provided");
        }

        EnumMap<MinecraftVersion, ItemCatalog> copy = new EnumMap<>(MinecraftVersion.class);
        copy.putAll(vanillaCatalogs);
        this.vanillaCatalogs = Collections.unmodifiableMap(copy);
        this.mergedSnapshots = new EnumMap<>(MinecraftVersion.class);
        this.logger = Objects.requireNonNull(logger, "logger");

        MinecraftVersion versionToUse = defaultVersion;
        if (versionToUse == null) {
            versionToUse = copy.keySet().iterator().next();
        }

        if (!this.vanillaCatalogs.containsKey(versionToUse)) {
            throw new IllegalArgumentException("Default version does not have a vanilla catalog: " + versionToUse);
        }
        this.activeVersion = versionToUse;
        this.logger.info("Version catalog initialised", StructuredLogger.field("activeVersion", activeVersion));
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
        if (version == activeVersion) {
            return;
        }
        activeVersion = version;
        invalidateSnapshots();
        logger.info("Active version updated", StructuredLogger.field("activeVersion", version));
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
        List<ItemCatalog> sanitizedMods = sanitize(modCatalogs);
        MinecraftVersion version = getActiveVersion();
        Snapshot cached = mergedSnapshots.get(version);
        if (cached != null && cached.matches(sanitizedMods)) {
            return cached.catalog();
        }

        List<ItemCatalog> catalogsToMerge = new ArrayList<>(1 + sanitizedMods.size());
        catalogsToMerge.add(getVanillaItems());
        catalogsToMerge.addAll(sanitizedMods);

        ItemCatalog merged;
        if (catalogsToMerge.size() == 1) {
            merged = catalogsToMerge.get(0);
        } else {
            merged = new CombinedItemCatalog(catalogsToMerge);
        }

        mergedSnapshots.put(version, new Snapshot(sanitizedMods, merged));
        int resultCount = merged.items() != null ? merged.items().size() : 0;
        logger.info("Merged item catalogs",
                StructuredLogger.field("version", version),
                StructuredLogger.field("mods", sanitizedMods.size()),
                StructuredLogger.field("resultItems", resultCount));
        return merged;
    }

    /**
     * Clears all cached merged snapshots. Should be invoked when mod jars are rescanned
     * to ensure fresh catalog data is produced on the next merge operation.
     */
    public void invalidateSnapshots() {
        mergedSnapshots.clear();
        logger.debug("Version catalog snapshots invalidated", StructuredLogger.field("activeVersion", activeVersion));
    }

    private static List<ItemCatalog> sanitize(List<ItemCatalog> modCatalogs) {
        if (modCatalogs == null || modCatalogs.isEmpty()) {
            return List.of();
        }
        List<ItemCatalog> sanitized = new ArrayList<>(modCatalogs.size());
        for (ItemCatalog catalog : modCatalogs) {
            if (catalog != null) {
                sanitized.add(catalog);
            }
        }
        if (sanitized.isEmpty()) {
            return List.of();
        }
        return List.copyOf(sanitized);
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

    private record Snapshot(List<ItemCatalog> modCatalogs, ItemCatalog catalog) {
        Snapshot {
            Objects.requireNonNull(modCatalogs, "modCatalogs");
            Objects.requireNonNull(catalog, "catalog");
        }

        boolean matches(List<ItemCatalog> otherModCatalogs) {
            return modCatalogs.equals(otherModCatalogs);
        }
    }
}
