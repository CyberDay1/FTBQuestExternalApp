package dev.ftbq.editor.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoreDaoTest {

    @TempDir
    Path tempDir;

    @Test
    void smokeTestCreatesSchemaAndPersistsData() throws Exception {
        Path databasePath = tempDir.resolve("store.db");
        StoreDao.ItemEntity item = new StoreDao.ItemEntity(
                "minecraft:stone",
                "Stone",
                true,
                "minecraft",
                "Minecraft",
                "[\"building\"]",
                "textures/item/stone.png",
                "hash123",
                "minecraft.jar",
                "1.20.1",
                "item");

        try (Connection connection = Jdbc.open(databasePath)) {
            StoreDao dao = new StoreDao(connection);
            dao.upsertItem(item);
            assertEquals(Optional.of(item), dao.findItemById(item.id()));
            assertEquals(List.of(item), dao.listItems());

            StoreDao.LootTableEntity lootTable = new StoreDao.LootTableEntity("chests/spawn_bonus_chest", "{\"pools\":[]}");
            dao.upsertLootTable(lootTable);
            assertEquals(Optional.of(lootTable), dao.findLootTable(lootTable.name()));
            assertEquals(List.of(lootTable), dao.listLootTables());

            dao.setSetting("theme", "dark");
            assertEquals(Optional.of("dark"), dao.getSetting("theme"));
        }

        try (Connection connection = Jdbc.open(databasePath)) {
            StoreDao dao = new StoreDao(connection);
            assertTrue(dao.findItemById("minecraft:stone").isPresent());
            assertEquals(1, dao.listItems().size());
            assertEquals(1, dao.listLootTables().size());
            assertEquals(Optional.of("dark"), dao.getSetting("theme"));
        }
    }
}
