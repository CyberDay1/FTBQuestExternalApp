package dev.ftbq.editor.io;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestFileJsonTest {
    @TempDir
    Path tempDir;

    @Test
    void roundTripPersistsStructure() throws IOException {
        Quest quest = new Quest(
                "quest-1",
                "Quest One",
                "Complete a task",
                new IconRef("quest_icon", Optional.of("assets/example/quest_icon.png")),
                List.of(
                        new ItemTask(new ItemRef("minecraft:stone", 4), true),
                        new AdvancementTask("minecraft:story/root"),
                        new LocationTask("minecraft:overworld", 10.0, 64.0, -5.0, 3.5)
                ),
                List.of(
                        new ItemReward(new ItemRef("minecraft:diamond", 1)),
                        new XpReward(25),
                        new CommandReward("/say hello", true),
                        new CustomReward("mod:bonus", Map.of("key", "value"))
                ),
                List.of(new Dependency("quest-0", true)),
                Visibility.SECRET
        );

        Chapter chapter = new Chapter(
                "chapter-1",
                "Chapter One",
                new IconRef("chapter_icon", Optional.of("assets/example/chapter_icon.png")),
                new BackgroundRef(
                        "chapter_background",
                        Optional.of("assets/example/backgrounds/chapter_background.png"),
                        Optional.of(BackgroundAlignment.CENTER),
                        Optional.of(BackgroundRepeat.BOTH)
                ),
                List.of(quest),
                Visibility.HIDDEN
        );

        ChapterGroup group = new ChapterGroup(
                "group-1",
                "Main Group",
                new IconRef("group_icon", Optional.of("assets/example/group_icon.png")),
                List.of("chapter-1"),
                Visibility.VISIBLE
        );

        LootTable lootTable = new LootTable(
                "example:tables/sample",
                List.of(
                        new LootPool(
                                "pool-1",
                                1,
                                List.of(new LootEntry(new ItemRef("minecraft:apple", 1), 0.75)),
                                List.of(new LootCondition("minecraft:survives_explosion", Map.of())),
                                List.of(new LootFunction("minecraft:set_count", Map.of("count", 2)))
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

        QuestFileJson.save(questFile, tempDir);
        QuestFile loaded = QuestFileJson.load(tempDir);

        assertEquals(questFile, loaded);
    }
}
