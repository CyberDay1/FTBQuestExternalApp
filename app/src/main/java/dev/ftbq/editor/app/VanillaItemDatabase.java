package dev.ftbq.editor.app;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.ftbq.editor.io.JsonConfig;
import dev.ftbq.editor.store.StoreDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Provides simple persistence utilities for the locally imported item catalog.
 */
public final class VanillaItemDatabase {
    private static final int MAX_ITEMS = 100_000;
    private static final TypeReference<List<StoreDao.ItemEntity>> ITEM_LIST_TYPE =
            new TypeReference<>() { };

    private final StoreDao storeDao;

    public VanillaItemDatabase(StoreDao storeDao) {
        this.storeDao = Objects.requireNonNull(storeDao, "storeDao");
    }

    /**
     * Reads all items currently present in the backing {@link StoreDao}.
     */
    public List<StoreDao.ItemEntity> listAllItems() {
        synchronized (storeDao) {
            return storeDao.listItems(
                    null,
                    List.of(),
                    null,
                    null,
                    null,
                    StoreDao.SortMode.NAME,
                    MAX_ITEMS,
                    0
            );
        }
    }

    /**
     * Serialises the supplied items to JSON and writes them to the provided file.
     */
    public void save(Path file, List<StoreDao.ItemEntity> items) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(items, "items");
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        JsonConfig.OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), items);
    }

    /**
     * Exports all items currently available to the specified file.
     */
    public void saveAll(Path file) throws IOException {
        save(file, listAllItems());
    }

    /**
     * Reads item entities from the given JSON file.
     */
    public List<StoreDao.ItemEntity> read(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        return JsonConfig.OBJECT_MAPPER.readValue(file.toFile(), ITEM_LIST_TYPE);
    }

    /**
     * Imports the supplied items into the {@link StoreDao}, upserting existing rows.
     */
    public void importItems(List<StoreDao.ItemEntity> items) {
        Objects.requireNonNull(items, "items");
        synchronized (storeDao) {
            for (StoreDao.ItemEntity item : items) {
                if (item != null) {
                    storeDao.upsertItem(item);
                }
            }
        }
    }

    /**
     * Serialises items to a JSON byte array for embedding in archive formats.
     */
    public byte[] toJsonBytes(List<StoreDao.ItemEntity> items) throws IOException {
        Objects.requireNonNull(items, "items");
        return JsonConfig.OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(items);
    }

    /**
     * Deserialises items from a JSON byte array.
     */
    public List<StoreDao.ItemEntity> fromJsonBytes(byte[] data) throws IOException {
        Objects.requireNonNull(data, "data");
        return JsonConfig.OBJECT_MAPPER.readValue(data, ITEM_LIST_TYPE);
    }
}
