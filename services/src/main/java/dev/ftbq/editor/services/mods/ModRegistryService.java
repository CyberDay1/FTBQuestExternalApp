package dev.ftbq.editor.services.mods;

import dev.ftbq.editor.ingest.ItemCatalog;
import dev.ftbq.editor.ingest.ItemCatalogExtractor;
import dev.ftbq.editor.ingest.ItemMeta;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caches metadata about imported mod JARs for downstream selection in the UI layer.
 */
public final class ModRegistryService {

    public static final int MAX_SELECTION = 10;

    private static final Logger LOGGER = LoggerFactory.getLogger(ModRegistryService.class);

    private final Map<String, RegisteredMod> modsById = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Consumer<List<RegisteredMod>>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Register a mod JAR with the registry by scanning it for metadata.
     *
     * @param jarPath     path to the uploaded JAR
     * @param sourceLabel human readable label (usually the file name)
     * @param versionHint version associated with the JAR
     * @return snapshot of the registry after registration
     * @throws IOException if the archive cannot be read
     */
    public List<RegisteredMod> register(Path jarPath, String sourceLabel, String versionHint) throws IOException {
        Objects.requireNonNull(jarPath, "jarPath");
        Objects.requireNonNull(sourceLabel, "sourceLabel");
        Objects.requireNonNull(versionHint, "versionHint");

        ItemCatalog catalog = ItemCatalogExtractor.extract(jarPath, sourceLabel, versionHint, false);
        return register(catalog);
    }

    /**
     * Registers all mods contained in the supplied catalog.
     *
     * @param catalog catalog to register
     * @return snapshot of the registry after registration
     */
    public List<RegisteredMod> register(ItemCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        Map<String, RegisteredMod> discovered = discoverMods(catalog);
        if (discovered.isEmpty()) {
            LOGGER.info("No mod metadata discovered | source={} version={}", catalog.source(), catalog.version());
            return listMods();
        }

        List<RegisteredMod> snapshot;
        synchronized (this) {
            discovered.values().forEach(mod -> modsById.put(normalizeKey(mod.modId()), mod));
            LOGGER.info("Registered mods from catalog | source={} version={} count={}", catalog.source(), catalog.version(),
                    discovered.size());
            snapshot = snapshot();
        }
        notifyListeners(snapshot);
        return snapshot;
    }

    /**
     * Replaces the registry contents with the supplied mods.
     * Primarily intended for tests.
     */
    synchronized void replaceAll(Collection<RegisteredMod> mods) {
        modsById.clear();
        if (mods != null) {
            mods.forEach(mod -> modsById.put(normalizeKey(mod.modId()), mod));
        }
        notifyListeners(snapshot());
    }

    /**
     * Removes all registered mods.
     */
    public void clear() {
        boolean changed = false;
        synchronized (this) {
            if (modsById.isEmpty()) {
                return;
            }
            modsById.clear();
            changed = true;
        }
        if (changed) {
            LOGGER.info("Cleared mod registry");
            notifyListeners(List.of());
        }
    }

    /**
     * Returns an immutable snapshot of all registered mods.
     */
    public synchronized List<RegisteredMod> listMods() {
        return snapshot();
    }

    /**
     * Looks up a registered mod by its identifier.
     */
    public synchronized Optional<RegisteredMod> find(String modId) {
        if (modId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(modsById.get(normalizeKey(modId)));
    }

    /**
     * Registers a listener that will be invoked whenever the registry changes.
     */
    public void addListener(Consumer<List<RegisteredMod>> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(Consumer<List<RegisteredMod>> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(List<RegisteredMod> snapshot) {
        for (Consumer<List<RegisteredMod>> listener : listeners) {
            try {
                listener.accept(snapshot);
            } catch (RuntimeException ex) {
                LOGGER.warn("Mod registry listener threw exception", ex);
            }
        }
    }

    private Map<String, RegisteredMod> discoverMods(ItemCatalog catalog) {
        Map<String, List<ItemMeta>> itemsByMod = catalog.items().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.modId() != null && !item.modId().isBlank())
                .collect(Collectors.groupingBy(item -> normalizeKey(item.modId()), LinkedHashMap::new, Collectors.toList()));

        Map<String, RegisteredMod> discovered = new LinkedHashMap<>();
        for (Map.Entry<String, List<ItemMeta>> entry : itemsByMod.entrySet()) {
            String modKey = entry.getKey();
            List<ItemMeta> metas = entry.getValue();
            Set<String> itemIds = new LinkedHashSet<>();
            String name = null;
            String version = null;
            for (ItemMeta meta : metas) {
                if (meta.id() != null && !meta.id().isBlank()) {
                    itemIds.add(meta.id());
                }
                if (name == null || name.isBlank()) {
                    name = sanitize(meta.modName());
                }
                if (version == null || version.isBlank()) {
                    version = sanitize(meta.modVersion());
                }
            }
            if (itemIds.isEmpty()) {
                continue;
            }
            List<String> sortedIds = new ArrayList<>(itemIds);
            Collections.sort(sortedIds);
            String effectiveName = name != null && !name.isBlank() ? name : modKey;
            String effectiveVersion = version != null && !version.isBlank() ? version : catalog.version();
            RegisteredMod mod = new RegisteredMod(modKey, effectiveName, effectiveVersion, sortedIds, catalog.source());
            discovered.put(modKey, mod);
        }
        return discovered;
    }

    private List<RegisteredMod> snapshot() {
        return List.copyOf(modsById.values());
    }

    private static String normalizeKey(String modId) {
        return sanitize(modId).toLowerCase(Locale.ROOT);
    }

    private static String sanitize(String value) {
        return value == null ? null : value.trim();
    }
}
