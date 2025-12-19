package dev.ftbq.editor.io.snbt;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ftbq.editor.domain.AdvancementTask;
import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.ItemReward;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.LocationTask;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.domain.RewardCommand;
import dev.ftbq.editor.domain.Visibility;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnbtQuestMapperTest {

    private final SnbtQuestMapper mapper = new SnbtQuestMapper();

    @Test
    void serialisesQuestFileToExpectedSnbt() {
        var quest = new Quest(
                "quest_1",
                "Getting Started",
                "Collect wood",
                new IconRef("minecraft:book"),
                List.of(
                        new ItemTask(new ItemRef("minecraft:oak_log", 16), true),
                        new AdvancementTask("minecraft:adventure/root"),
                        new LocationTask("minecraft:overworld", 128.0, 64.25, -12.5, 5.0)
                ),
                List.of(new ItemReward(new ItemRef("minecraft:apple", 3))),
                50,
                null,
                null,
                new RewardCommand("/say Hello", true),
                List.of(new Dependency("intro", true), new Dependency("optional", false)),
                Visibility.VISIBLE,
                null,
                null,
                null,
                null
        );

        var chapter = new Chapter(
                "chapter_1",
                "Basics",
                new IconRef("minecraft:book"),
                new BackgroundRef("minecraft:textures/gui/default.png"),
                List.of(quest),
                Visibility.SECRET
        );

        var file = new QuestFile(
                "pack_id",
                "Pack Title",
                List.of(),
                List.of(chapter),
                List.of()
        );

        var snbt = mapper.toSnbt(file);

        assertTrue(snbt.contains("id:\"pack_id\""));
        assertTrue(snbt.contains("title:\"Pack Title\""));
        assertTrue(snbt.contains("title:\"Basics\""));
        assertTrue(snbt.contains("title:\"Getting Started\""));
        assertTrue(snbt.contains("type:\"item\""));
        assertTrue(snbt.contains("type:\"advancement\""));
        assertTrue(snbt.contains("type:\"location\""));
        assertTrue(snbt.contains("visibility:\"secret\""));
        assertTrue(snbt.contains("visibility:\"visible\""));
        assertTrue(snbt.contains("type:\"xp_amount\""));
        assertTrue(snbt.contains("amount:50"));
    }
}
