package dev.ftbq.editor.viewmodel;

import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.LootEntry;
import dev.ftbq.editor.domain.LootPool;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.domain.version.ItemCatalog;
import dev.ftbq.editor.domain.version.VersionCatalog;
import dev.ftbq.editor.store.StoreDao;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * View model for the loot table editor screen.
 */
public class LootTableEditorViewModel {
    private final StoreDao storeDao;
    private final VersionCatalog versionCatalog;

    private final StringProperty tableName = new SimpleStringProperty("");
    private final ObservableList<LootPool> pools = FXCollections.observableArrayList();
    private final ObjectProperty<LootPool> selectedPool = new SimpleObjectProperty<>();
    private final ObservableList<LootEntryRow> entries = FXCollections.observableArrayList();

    private final Map<String, StoreDao.ItemEntity> itemCache = new HashMap<>();

    public LootTableEditorViewModel(StoreDao storeDao, VersionCatalog versionCatalog) {
        this.storeDao = Objects.requireNonNull(storeDao, "storeDao");
        this.versionCatalog = Objects.requireNonNull(versionCatalog, "versionCatalog");
        selectedPool.addListener((obs, oldPool, newPool) -> refreshEntries(newPool));
        pools.addListener((ListChangeListener<LootPool>) change -> {
            if (!pools.contains(selectedPool.get())) {
                if (pools.isEmpty()) {
                    selectedPool.set(null);
                } else if (!pools.isEmpty()) {
                    selectedPool.set(pools.get(0));
                }
            }
        });
    }

    public StringProperty tableNameProperty() {
        return tableName;
    }

    public ObservableList<LootPool> getPools() {
        return pools;
    }

    public ObjectProperty<LootPool> selectedPoolProperty() {
        return selectedPool;
    }

    public ObservableList<LootEntryRow> getEntries() {
        return entries;
    }

    public void loadLootTable(LootTable table) {
        Objects.requireNonNull(table, "table");
        tableName.set(table.id());
        pools.setAll(table.pools());
        selectedPool.set(pools.isEmpty() ? null : pools.get(0));
    }

    public LootTable toLootTable() {
        List<LootPool> poolCopies = pools.stream()
                .map(this::copyPool)
                .collect(Collectors.toCollection(ArrayList::new));
        return new LootTable(tableName.get(), poolCopies);
    }

    public LootPool addPool() {
        String baseName = "Pool";
        int suffix = 1;
        while (poolNameExists(baseName + " " + suffix)) {
            suffix++;
        }
        String poolName = baseName + " " + suffix;
        LootPool newPool = new LootPool(poolName, 1, List.of(), List.of(), List.of());
        pools.add(newPool);
        selectedPool.set(newPool);
        return newPool;
    }

    public void addEntry(ItemRef itemRef) {
        Objects.requireNonNull(itemRef, "itemRef");
        LootPool currentPool = selectedPool.get();
        if (currentPool == null) {
            return;
        }
        LootEntry entry = new LootEntry(itemRef, 1.0);
        List<LootEntry> updatedEntries = new ArrayList<>(currentPool.entries());
        updatedEntries.add(entry);
        LootPool updatedPool = withEntries(currentPool, updatedEntries);
        replacePool(currentPool, updatedPool);
        selectedPool.set(updatedPool);
    }

    public void updateEntryWeight(LootEntryRow row, double weight) {
        Objects.requireNonNull(row, "row");
        if (weight <= 0) {
            throw new IllegalArgumentException("weight must be positive");
        }
        LootPool currentPool = selectedPool.get();
        if (currentPool == null) {
            return;
        }
        List<LootEntry> updated = currentPool.entries().stream()
                .map(entry -> entry.equals(row.entry()) ? new LootEntry(entry.item(), weight) : entry)
                .toList();
        LootPool updatedPool = withEntries(currentPool, updated);
        replacePool(currentPool, updatedPool);
        selectedPool.set(updatedPool);
    }

    public void removeSelectedEntries(Collection<LootEntryRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LootPool currentPool = selectedPool.get();
        if (currentPool == null) {
            return;
        }
        List<LootEntry> filtered = currentPool.entries().stream()
                .filter(entry -> rows.stream().noneMatch(row -> row.entry().equals(entry)))
                .toList();
        LootPool updatedPool = withEntries(currentPool, filtered);
        replacePool(currentPool, updatedPool);
        selectedPool.set(updatedPool);
    }

    public String computePreview() {
        LootPool pool = selectedPool.get();
        if (pool == null || pool.entries().isEmpty()) {
            return "No entries to preview.";
        }
        double totalWeight = pool.entries().stream()
                .mapToDouble(LootEntry::weight)
                .sum();
        if (totalWeight <= 0) {
            return "Total weight is zero.";
        }
        List<String> parts = new ArrayList<>();
        for (LootEntry entry : pool.entries()) {
            double percentage = (entry.weight() / totalWeight) * 100.0;
            String label = lookupEntryDisplayName(entry.item().itemId());
            parts.add(String.format("%s: %.1f%%", label, percentage));
        }
        return String.join(", ", parts);
    }

    private void replacePool(LootPool oldPool, LootPool newPool) {
        int index = pools.indexOf(oldPool);
        if (index >= 0) {
            pools.set(index, newPool);
        }
    }

    private LootPool withEntries(LootPool source, List<LootEntry> newEntries) {
        return new LootPool(
                source.name(),
                source.rolls(),
                newEntries,
                source.conditions(),
                source.functions()
        );
    }

    private LootPool copyPool(LootPool pool) {
        return new LootPool(
                pool.name(),
                pool.rolls(),
                new ArrayList<>(pool.entries()),
                new ArrayList<>(pool.conditions()),
                new ArrayList<>(pool.functions())
        );
    }

    private void refreshEntries(LootPool pool) {
        if (pool == null) {
            entries.clear();
            return;
        }
        List<LootEntryRow> rows = pool.entries().stream()
                .map(this::toRow)
                .toList();
        entries.setAll(rows);
    }

    private boolean poolNameExists(String candidate) {
        return pools.stream().anyMatch(pool -> pool.name().equalsIgnoreCase(candidate));
    }

    private LootEntryRow toRow(LootEntry entry) {
        StoreDao.ItemEntity entity = itemCache.computeIfAbsent(entry.item().itemId(), id ->
                storeDao.findItemById(id).orElse(null));
        boolean valid = isValidForVersion(entry.item().itemId());
        return new LootEntryRow(entry, entity, valid);
    }

    private boolean isValidForVersion(String itemId) {
        ItemCatalog vanilla = versionCatalog.getVanillaItems();
        if (vanilla == null) {
            return true;
        }
        Collection<ItemRef> items = vanilla.items();
        if (items == null) {
            return true;
        }
        return items.stream().anyMatch(item -> item != null && item.itemId().equals(itemId));
    }

    private String lookupEntryDisplayName(String itemId) {
        StoreDao.ItemEntity entity = itemCache.computeIfAbsent(itemId, id ->
                storeDao.findItemById(id).orElse(null));
        if (entity != null && entity.displayName() != null && !entity.displayName().isBlank()) {
            return entity.displayName();
        }
        return itemId;
    }

    public record LootEntryRow(LootEntry entry, StoreDao.ItemEntity itemEntity, boolean validInActiveVersion) {
        public String itemId() {
            return entry.item().itemId();
        }

        public double weight() {
            return entry.weight();
        }

        public String displayName() {
            if (itemEntity != null && itemEntity.displayName() != null && !itemEntity.displayName().isBlank()) {
                return itemEntity.displayName();
            }
            return entry.item().itemId();
        }

        public Optional<String> iconHash() {
            return Optional.ofNullable(itemEntity).map(StoreDao.ItemEntity::iconHash);
        }

        public String conditionsSummary() {
            return validInActiveVersion ? "Valid" : "Missing in version";
        }
    }
}


