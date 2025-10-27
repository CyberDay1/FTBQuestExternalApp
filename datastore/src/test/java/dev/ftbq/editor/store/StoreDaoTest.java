package dev.ftbq.editor.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

import dev.ftbq.editor.domain.Quest;
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
            assertEquals(
                    List.of(item),
                    dao.listItems(null, List.of(), null, null, null, StoreDao.SortMode.NAME, Integer.MAX_VALUE, 0));

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
            assertEquals(
                    1,
                    dao.listItems(null, List.of(), null, null, null, StoreDao.SortMode.NAME, Integer.MAX_VALUE, 0)
                            .size());
            assertEquals(1, dao.listLootTables().size());
            assertEquals(Optional.of("dark"), dao.getSetting("theme"));
        }
    }

    @Test
    void listItemsSupportsFilteringSortingAndPagination() throws Exception {
        try (Connection connection = Jdbc.openInMemory()) {
            StoreDao dao = new StoreDao(connection);
            StoreDao.ItemEntity apple = new StoreDao.ItemEntity(
                    "minecraft:apple",
                    "Apple",
                    true,
                    "minecraft",
                    "Minecraft",
                    "[\"food\",\"fruit\"]",
                    null,
                    null,
                    null,
                    "1.20.1",
                    "item");
            StoreDao.ItemEntity berry = new StoreDao.ItemEntity(
                    "mod:berry",
                    "Sweet Berry",
                    false,
                    "modid",
                    "Modded Foods",
                    "[\"food\",\"berry\"]",
                    null,
                    null,
                    null,
                    "1.20.1",
                    "item");
            StoreDao.ItemEntity block = new StoreDao.ItemEntity(
                    "mod:block",
                    "Fancy Block",
                    false,
                    "buildingmod",
                    "Building Blocks",
                    "[\"building\"]",
                    null,
                    null,
                    null,
                    "1.18",
                    "block");

            dao.upsertItem(apple);
            dao.upsertItem(berry);
            dao.upsertItem(block);

            assertEquals(
                    List.of(apple),
                    dao.listItems("apple", List.of(), null, null, null, StoreDao.SortMode.NAME, Integer.MAX_VALUE, 0));

            assertEquals(
                    List.of(apple, berry),
                    dao.listItems(
                            null,
                            List.of("food"),
                            null,
                            null,
                            "item",
                            StoreDao.SortMode.NAME,
                            Integer.MAX_VALUE,
                            0));

            assertEquals(
                    List.of(berry),
                    dao.listItems(
                            null,
                            List.of("berry"),
                            "Modded Foods",
                            "1.20.1",
                            "item",
                            StoreDao.SortMode.NAME,
                            Integer.MAX_VALUE,
                            0));

            assertEquals(
                    List.of(block, apple, berry),
                    dao.listItems(null, List.of(), null, null, null, StoreDao.SortMode.MOD, Integer.MAX_VALUE, 0));

            assertEquals(
                    List.of(apple, block),
                    dao.listItems(null, List.of(), null, null, null, StoreDao.SortMode.VANILLA_FIRST, 2, 0));

            assertIterableEquals(
                    List.of(block),
                    dao.listItems(null, List.of(), null, null, null, StoreDao.SortMode.VANILLA_FIRST, 1, 1));
        }
    }

    @Test
    void saveQuestPersistsQuestData() throws Exception {
        try (Connection connection = Jdbc.openInMemory()) {
            StoreDao dao = new StoreDao(connection);
            Quest quest = Quest.builder()
                    .id("quest-1")
                    .title("Test Quest")
                    .description("First version")
                    .build();

            dao.saveQuest(quest);

            assertEquals(quest.toString(), loadQuestData(connection, "quest-1"));

            Quest updatedQuest = Quest.builder()
                    .id("quest-1")
                    .title("Test Quest")
                    .description("Updated version")
                    .build();

            dao.saveQuest(updatedQuest);

            assertEquals(updatedQuest.toString(), loadQuestData(connection, "quest-1"));
        }
    }

    private static String loadQuestData(Connection connection, String questId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT data FROM quests WHERE id = ?")) {
            statement.setString(1, questId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Quest not found: " + questId);
                }
                return resultSet.getString("data");
            }
        }
    }
}
