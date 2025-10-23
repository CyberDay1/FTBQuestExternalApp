package dev.ftbq.editor.support;

import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.version.ItemCatalog;
import dev.ftbq.editor.domain.version.MinecraftVersion;
import dev.ftbq.editor.domain.version.VersionCatalog;
import dev.ftbq.editor.store.StoreDao;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal {@link VersionCatalog} that sources item references from the local {@link StoreDao}.
 */
final class StoreBackedVersionCatalog implements VersionCatalog {
    private static final int MAX_ITEMS = 8192;

    private final StoreDao storeDao;
    private final AtomicReference<ItemCatalog> cachedCatalog = new AtomicReference<>();
    private MinecraftVersion activeVersion = MinecraftVersion.V1_20_1;

    StoreBackedVersionCatalog(StoreDao storeDao) {
        this.storeDao = Objects.requireNonNull(storeDao, "storeDao");
    }

    @Override
    public MinecraftVersion getActiveVersion() {
        return activeVersion;
    }

    @Override
    public void setActiveVersion(MinecraftVersion version) {
        Objects.requireNonNull(version, "version");
        if (version != activeVersion) {
            activeVersion = version;
            cachedCatalog.set(null);
        }
    }

    @Override
    public ItemCatalog getVanillaItems() {
        ItemCatalog catalog = cachedCatalog.get();
        if (catalog == null) {
            catalog = loadCatalog();
            cachedCatalog.set(catalog);
        }
        return catalog;
    }

    @Override
    public ItemCatalog mergeWithMods(List<ItemCatalog> modCatalogs) {
        // Mods are not modelled in the UI yet; return vanilla catalog.
        return getVanillaItems();
    }

    private ItemCatalog loadCatalog() {
        List<StoreDao.ItemEntity> entities = storeDao.listItems(
                null,
                List.of(),
                null,
                null,
                null,
                StoreDao.SortMode.NAME,
                MAX_ITEMS,
                0
        );
        List<ItemRef> items = entities.stream()
                .map(entity -> new ItemRef(entity.id(), 1))
                .toList();
        return new SimpleItemCatalog(items);
    }

    private record SimpleItemCatalog(Collection<ItemRef> items) implements ItemCatalog {
    }
}
