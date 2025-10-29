package dev.ftbq.editor.app;

import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.ChapterGroup;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.LootEntry;
import dev.ftbq.editor.domain.LootPool;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.store.Jdbc;
import dev.ftbq.editor.store.StoreDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectFileHandlerTest {

    private Connection connection;
    private StoreDao storeDao;
    private ProjectFileHandler handler;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        connection = Jdbc.openInMemory();
        storeDao = new StoreDao(connection);
        handler = new ProjectFileHandler(storeDao);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void saveProjectWritesQuestContentAndItems() throws Exception {
        StoreDao.ItemEntity item = new StoreDao.ItemEntity(
                "minecraft:stone",
                "Stone",
                true,
                "minecraft",
                "Minecraft",
                null,
                null,
                null,
                "vanilla.jar",
                "1.20.4",
                "basic"
        );
        storeDao.upsertItem(item);

        QuestFile questFile = sampleQuestFile();
        Path projectFile = tempDir.resolve("project.ftbq");
        handler.saveProject(projectFile, questFile);

        Map<String, byte[]> entries = readZipEntries(projectFile);
        assertTrue(entries.containsKey("quest_file.json"));
        assertTrue(entries.containsKey("chapters/chapter_one.json"));
        assertTrue(entries.containsKey("chapter_groups/group_one.json"));
        assertTrue(entries.containsKey("loot_tables/example/starter.json"));
        assertTrue(entries.containsKey("items/items.json"));

        VanillaItemDatabase database = new VanillaItemDatabase(storeDao);
        List<StoreDao.ItemEntity> items = database.fromJsonBytes(entries.get("items/items.json"));
        assertEquals(1, items.size());
        assertEquals(item.id(), items.get(0).id());
    }

    @Test
    void exportQuestPackOmitsItemDatabase() throws Exception {
        storeDao.upsertItem(new StoreDao.ItemEntity(
                "minecraft:dirt",
                "Dirt",
                true,
                "minecraft",
                "Minecraft",
                null,
                null,
                null,
                null,
                "1.20.4",
                "basic"
        ));

        QuestFile questFile = sampleQuestFile();
        Path zipFile = tempDir.resolve("quest-pack.zip");
        handler.exportQuestPack(zipFile, questFile);

        Map<String, byte[]> entries = readZipEntries(zipFile);
        assertTrue(entries.containsKey("quest_file.json"));
        assertTrue(entries.containsKey("chapters/chapter_one.json"));
        assertTrue(entries.containsKey("chapter_groups/group_one.json"));
        assertTrue(entries.containsKey("loot_tables/example/starter.json"));
        assertFalse(entries.containsKey("items/items.json"));
    }

    @Test
    void loadProjectRestoresQuestFileAndItems() throws Exception {
        StoreDao.ItemEntity item = new StoreDao.ItemEntity(
                "minecraft:iron_ingot",
                "Iron Ingot",
                true,
                "minecraft",
                "Minecraft",
                null,
                null,
                null,
                "vanilla.jar",
                "1.20.4",
                "basic"
        );
        storeDao.upsertItem(item);
        QuestFile questFile = sampleQuestFile();
        Path projectFile = tempDir.resolve("saved.ftbq");
        handler.saveProject(projectFile, questFile);

        try (Connection otherConnection = Jdbc.openInMemory()) {
            StoreDao otherStore = new StoreDao(otherConnection);
            ProjectFileHandler otherHandler = new ProjectFileHandler(otherStore);
            assertTrue(otherStore.listItems(null, List.of(), null, null, null, StoreDao.SortMode.NAME, 10, 0).isEmpty());

            ProjectFileHandler.ProjectData data = otherHandler.loadProject(projectFile);

            assertEquals(questFile, data.questFile());
            assertEquals(1, data.items().size());
            assertEquals(item.id(), data.items().get(0).id());

            List<StoreDao.ItemEntity> imported = otherStore.listItems(null, List.of(), null, null, null, StoreDao.SortMode.NAME, 10, 0);
            assertEquals(1, imported.size());
            assertEquals(item.id(), imported.get(0).id());
        }
    }

    private QuestFile sampleQuestFile() {
        Quest quest = new Quest(
                "quest_one",
                "Quest One",
                "Collect the shiny thing",
                new IconRef("minecraft:book"),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                List.of(),
                Visibility.VISIBLE
        );

        Chapter chapter = new Chapter(
                "chapter_one",
                "Chapter One",
                new IconRef("minecraft:book"),
                new BackgroundRef("minecraft:textures/gui/default.png"),
                List.of(quest),
                Visibility.VISIBLE
        );

        ChapterGroup group = new ChapterGroup(
                "group_one",
                "Group One",
                new IconRef("minecraft:stone"),
                List.of(chapter.id()),
                Visibility.VISIBLE
        );

        LootEntry entry = new LootEntry(new ItemRef("minecraft:diamond", 1), 1.0);
        LootPool pool = new LootPool("main", 1, List.of(entry), List.of(), List.of());
        LootTable lootTable = LootTable.builder()
                .id("example:starter")
                .addPool(pool)
                .iconId("minecraft:chest")
                .build();

        return new QuestFile(
                "sample_pack",
                "Sample Pack",
                List.of(group),
                List.of(chapter),
                List.of(lootTable)
        );
    }

    private Map<String, byte[]> readZipEntries(Path zipPath) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
                zip.closeEntry();
            }
        }
        return entries;
    }
}
