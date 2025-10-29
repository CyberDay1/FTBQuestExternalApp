package dev.ftbq.editor.app;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.ChapterGroup;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.io.JsonConfig;
import dev.ftbq.editor.store.StoreDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Reads and writes consolidated project archives that include quests, loot tables and item data.
 */
public final class ProjectFileHandler {
    private static final String QUEST_FILE_ENTRY = "quest_file.json";
    private static final String CHAPTERS_DIR = "chapters/";
    private static final String CHAPTER_GROUPS_DIR = "chapter_groups/";
    private static final String LOOT_TABLES_DIR = "loot_tables/";
    private static final String ITEMS_ENTRY = "items/items.json";

    private final VanillaItemDatabase itemDatabase;

    public ProjectFileHandler(StoreDao storeDao) {
        Objects.requireNonNull(storeDao, "storeDao");
        this.itemDatabase = new VanillaItemDatabase(storeDao);
    }

    /**
     * Writes a project archive that contains quests, chapters, groups, loot tables and imported items.
     */
    public void saveProject(Path target, QuestFile questFile) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(questFile, "questFile");
        writeArchive(target, questFile, true);
    }

    /**
     * Exports the quest pack as a zip archive, omitting the item database.
     */
    public void exportQuestPack(Path target, QuestFile questFile) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(questFile, "questFile");
        writeArchive(target, questFile, false);
    }

    /**
     * Loads the quest file and item database from a project archive. Imported items are immediately
     * persisted in the backing {@link StoreDao}.
     */
    public ProjectData loadProject(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        ProjectData data = readArchive(source);
        if (!data.items().isEmpty()) {
            itemDatabase.importItems(data.items());
        }
        return data;
    }

    private void writeArchive(Path target, QuestFile questFile, boolean includeItems) throws IOException {
        Path absoluteTarget = target.toAbsolutePath();
        Path parent = absoluteTarget.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(absoluteTarget))) {
            writeJsonEntry(zip, QUEST_FILE_ENTRY, questFile);
            for (Chapter chapter : questFile.chapters()) {
                writeJsonEntry(zip, CHAPTERS_DIR + sanitizeId(chapter.id()) + ".json", chapter);
            }
            for (ChapterGroup group : questFile.chapterGroups()) {
                writeJsonEntry(zip, CHAPTER_GROUPS_DIR + sanitizeId(group.id()) + ".json", group);
            }
            for (LootTable lootTable : questFile.lootTables()) {
                writeJsonEntry(zip, LOOT_TABLES_DIR + lootTableEntryName(lootTable.id()), lootTable);
            }
            if (includeItems) {
                writeJsonEntry(zip, ITEMS_ENTRY, itemDatabase.listAllItems());
            }
        }
    }

    private ProjectData readArchive(Path source) throws IOException {
        QuestFile questFile = null;
        List<StoreDao.ItemEntity> items = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(source))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                switch (name) {
                    case QUEST_FILE_ENTRY -> {
                        byte[] data = zip.readAllBytes();
                        questFile = JsonConfig.OBJECT_MAPPER.readValue(data, QuestFile.class);
                    }
                    case ITEMS_ENTRY -> items = new ArrayList<>(itemDatabase.fromJsonBytes(zip.readAllBytes()));
                    default -> { /* ignore other entries */ }
                }
                zip.closeEntry();
            }
        }
        if (questFile == null) {
            throw new IOException("Project archive missing quest_file.json: " + source);
        }
        return new ProjectData(questFile, items);
    }

    private void writeJsonEntry(ZipOutputStream zip, String entryName, Object value) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zip.putNextEntry(entry);
        byte[] data = JsonConfig.OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
        zip.write(data);
        zip.closeEntry();
    }

    private String sanitizeId(String id) {
        String safe = id == null ? "unknown" : id.trim();
        if (safe.isEmpty()) {
            safe = "entry";
        }
        return safe.replace(':', '_').replace('/', '_');
    }

    private String lootTableEntryName(String id) {
        String path = id;
        if (path == null || path.isBlank()) {
            path = "loot_table";
        }
        int colon = path.indexOf(':');
        String namespace = colon >= 0 ? path.substring(0, colon) : "minecraft";
        String pathPart = colon >= 0 ? path.substring(colon + 1) : path;
        String normalised = pathPart.replace('\n', '_').replace('\r', '_');
        if (normalised.isBlank()) {
            normalised = "table";
        }
        return String.format(Locale.ROOT, "%s/%s.json", namespace, normalised);
    }

    public record ProjectData(QuestFile questFile, List<StoreDao.ItemEntity> items) {
        public ProjectData {
            Objects.requireNonNull(questFile, "questFile");
            items = List.copyOf(Objects.requireNonNull(items, "items"));
        }
    }
}
