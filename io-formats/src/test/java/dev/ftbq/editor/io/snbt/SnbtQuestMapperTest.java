package dev.ftbq.editor.io.snbt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.ftbq.editor.domain.AdvancementTask;
import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.LocationTask;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.domain.Reward;
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
                List.of(
                        Reward.item(new ItemRef("minecraft:apple", 3)),
                        Reward.xpAmount(50),
                        Reward.command(new RewardCommand("/say Hello", true))
                ),
                List.of(new Dependency("intro", true), new Dependency("optional", false)),
                Visibility.VISIBLE
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

        var expected = """
{
  id:\"pack_id\",
  title:\"Pack Title\",
  chapters:[
    {
      id:\"chapter_1\",
      title:\"Basics\",
      icon:\"minecraft:book\",
      background:\"minecraft:textures/gui/default.png\",
      visibility:\"secret\",
      quests:[
        {
          id:\"quest_1\",
          title:\"Getting Started\",
          description:\"Collect wood\",
          icon:\"minecraft:book\",
          visibility:\"visible\",
          tasks:[{type:\"item\", item:{id:\"minecraft:oak_log\", count:16}, consume:1b}, {type:\"advancement\", advancement:\"minecraft:adventure/root\"}, {type:\"location\", dimension:\"minecraft:overworld\", x:128.0d, y:64.25d, z:-12.5d, radius:5.0d}],
          rewards:[{type:\"item\", item:{id:\"minecraft:apple\", count:3}}, {type:\"xp_amount\", amount:50}, {type:\"command\", command:\"/say Hello\", run_as_server:1b}],
          dependencies:[{quest:\"intro\", required:1b}, {quest:\"optional\", required:0b}]
        }
      ]
    }
  ]
}
""";

        assertEquals(expected, snbt);
    }
}
