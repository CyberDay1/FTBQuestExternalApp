package dev.ftbq.editor.io.importer;

import dev.ftbq.editor.domain.AdvancementTask;
import dev.ftbq.editor.domain.BackgroundAlignment;
import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.BackgroundRepeat;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.ChapterGroup;
import dev.ftbq.editor.domain.CommandReward;
import dev.ftbq.editor.domain.CustomReward;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.ItemReward;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.LocationTask;
import dev.ftbq.editor.domain.LootCondition;
import dev.ftbq.editor.domain.LootEntry;
import dev.ftbq.editor.domain.LootFunction;
import dev.ftbq.editor.domain.LootPool;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.domain.XpReward;
import dev.ftbq.editor.io.exporter.Exporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExporterImporterTest {
    @TempDir
    Path tempDir;

    @Test
    void exportThenImportRoundTrip() throws IOException {
        Path iconFile = tempDir.resolve("icon.txt");
        Files.writeString(iconFile, "icon");
        Path backgroundFile = tempDir.resolve("background.txt");
        Files.writeString(backgroundFile, "background");

        Quest quest = new Quest(
                "quest-1",
                "Quest One",
                "Complete the task",
                new IconRef(iconFile.toString(), Optional.of(tempDir.relativize(iconFile).toString())),
                List.of(
                        new ItemTask(new ItemRef("minecraft:stone", 4), true),
                        new AdvancementTask("minecraft:story/root"),
                        new LocationTask("minecraft:overworld", 5.0, 65.0, -10.0, 2.5)
                ),
                List.of(
                        new ItemReward(new ItemRef("minecraft:diamond", 2)),
                        new XpReward(15),
                        new CommandReward("/say exported", false),
                        new CustomReward("mod:bonus", Map.of("tier", 3))
                ),
                List.of(new Dependency("quest-0", true)),
                Visibility.VISIBLE
        );

        Chapter chapter = new Chapter(
                "chapter-1",
                "Chapter One",
                new IconRef(iconFile.toString(), Optional.of(tempDir.relativize(iconFile).toString())),
                new BackgroundRef(
                        backgroundFile.toString(),
                        Optional.of(tempDir.relativize(backgroundFile).toString()),
                        Optional.of(BackgroundAlignment.CENTER),
                        Optional.of(BackgroundRepeat.BOTH)
                ),
                List.of(quest),
                Visibility.HIDDEN
        );

        ChapterGroup group = new ChapterGroup(
                "group-1",
                "Main Group",
                new IconRef(iconFile.toString(), Optional.of(tempDir.relativize(iconFile).toString())),
                List.of("chapter-1"),
                Visibility.VISIBLE
        );

        LootTable lootTable = new LootTable(
                "example:tables/sample",
                List.of(
                        new LootPool(
                                "pool-1",
                                1,
                                List.of(new LootEntry(new ItemRef("minecraft:apple", 1), 1.0)),
                                List.of(new LootCondition("minecraft:survives_explosion", Map.of())),
                                List.of(new LootFunction("minecraft:set_count", Map.of("count", 1)))
                        )
                )
        );

        QuestFile questFile = QuestFile.builder()
                .id("example:pack")
                .title("Quest Title")
                .chapterGroups(List.of(group))
                .chapters(List.of(chapter))
                .lootTables(List.of(lootTable))
                .build();

        Path exportRoot = tempDir.resolve("export");
        new Exporter().exportPack(questFile, tempDir, exportRoot);

        Path assetIcon = exportRoot.resolve("assets/example/ftbquests/icons/" + iconFile.getFileName());
        Path assetBackground = exportRoot.resolve("assets/example/ftbquests/backgrounds/" + backgroundFile.getFileName());
        assertTrue(Files.exists(assetIcon));
        assertTrue(Files.exists(assetBackground));

        QuestFile imported = new Importer().importPack(exportRoot);

        assertEquals(questFile, imported);
    }

    @Test
    void missingRelativeAssetPathThrows() {
        Quest quest = Quest.builder()
                .id("quest-1")
                .title("Quest One")
                .description("desc")
                .icon(new IconRef("minecraft:book", Optional.of("icons/missing.txt")))
                .build();

        Chapter chapter = Chapter.builder()
                .id("chapter-1")
                .title("Chapter One")
                .addQuest(quest)
                .build();

        ChapterGroup group = ChapterGroup.builder()
                .id("group-1")
                .title("Group")
                .addChapterId("chapter-1")
                .build();

        QuestFile questFile = QuestFile.builder()
                .id("example:pack")
                .title("Quest Title")
                .chapterGroups(List.of(group))
                .chapters(List.of(chapter))
                .build();

        Path exportRoot = tempDir.resolve("export-missing");

        IOException exception = assertThrows(IOException.class, () -> new Exporter().exportPack(questFile, tempDir, exportRoot));
        assertTrue(exception.getMessage().contains("Missing icon asset at relative path: icons/missing.txt"));
    }
}
