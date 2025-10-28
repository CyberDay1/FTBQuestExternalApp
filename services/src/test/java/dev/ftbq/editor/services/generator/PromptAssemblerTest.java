package dev.ftbq.editor.services.generator;

import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.ChapterGroup;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.Visibility;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptAssemblerTest {

    private static final String EXAMPLE_SNBT = "{id:\"example_pack\",title:\"Example\",chapters:[]}";

    @Test
    void shouldAssembleRequiredBlocks() {
        PromptAssembler assembler = new PromptAssembler();
        GenerationContext context = sampleContext();

        ModelPrompt prompt = assembler.buildPrompt(context);

        List<String> blockKinds = prompt.blocks().stream()
                .map(block -> block.metadata().getOrDefault("block", ""))
                .collect(Collectors.toList());

        assertTrue(blockKinds.containsAll(List.of("system", "style-guide", "constraints", "output-contract", "pack-context")));
        assertTrue(blockKinds.stream().filter(kind -> kind.equals("example")).count() >= 1);

        PromptBlock systemBlock = prompt.blocks().get(0);
        assertEquals(PromptRole.SYSTEM, systemBlock.role());
        assertEquals("You produce FTB Quests chapters in SNBT. Follow exact key names and structures. Do not emit JSON.",
                systemBlock.content());

        PromptBlock exampleBlock = prompt.blocks().stream()
                .filter(block -> "example".equals(block.metadata().get("block")))
                .findFirst()
                .orElseThrow();
        assertEquals(EXAMPLE_SNBT, exampleBlock.content());
        assertEquals(EXAMPLE_SNBT.length(), exampleBlock.content().length(), "Example SNBT must remain verbatim");

        assertFalse(blockKinds.contains("system-nudge"));
    }

    @Test
    void shouldIncludeCorrectiveNudgeBlockWhenRequested() {
        PromptAssembler assembler = new PromptAssembler();
        GenerationContext context = sampleContext();

        ModelPrompt prompt = assembler.buildPromptWithNudge(context, "Unquoted keys near quests");

        PromptBlock nudgeBlock = prompt.blocks().get(prompt.blocks().size() - 1);
        assertEquals("system-nudge", nudgeBlock.metadata().get("block"));
        assertTrue(nudgeBlock.content().contains("Unquoted keys near quests"));
        assertEquals(PromptRole.SYSTEM, nudgeBlock.role());
    }

    private GenerationContext sampleContext() {
        QuestDesignSpec spec = new QuestDesignSpec(
                "automation",
                List.of("starter", "midgame", "endgame"),
                List.of("requires power", "tiered crafting"),
                List.of("tech", "base building"),
                Set.of("minecraft:bedrock"),
                List.of("item", "advancement"),
                5,
                12);

        ChapterGroup group = ChapterGroup.builder()
                .id("core")
                .title("Core")
                .addChapterId("existing_chapter")
                .build();

        LootTable lootTable = LootTable.builder()
                .id("loot/core")
                .build();

        QuestFile questFile = QuestFile.builder()
                .id("pack")
                .title("Example Pack")
                .addChapterGroup(group)
                .addLootTable(lootTable)
                .build();

        Quest exampleQuest = Quest.builder()
                .id("existing_quest")
                .title("Existing Quest")
                .description("A quest already present")
                .icon(new IconRef("minecraft:book"))
                .visibility(Visibility.VISIBLE)
                .addTask(new dev.ftbq.editor.domain.ItemTask("minecraft:stone", 1))
                .addReward(Reward.xpLevels(1))
                .dependencies(List.of(new Dependency("root", true)))
                .build();

        Chapter exampleChapter = Chapter.builder()
                .id("existing_chapter")
                .title("Existing Chapter")
                .icon(new IconRef("minecraft:chest"))
                .background(new BackgroundRef("minecraft:textures/gui/demo.png"))
                .visibility(Visibility.VISIBLE)
                .addQuest(exampleQuest)
                .build();

        ExampleChapterConstraint example = new ExampleChapterConstraint(
                Path.of("examples/core.snbt"),
                EXAMPLE_SNBT,
                List.of(exampleChapter));

        Map<String, Set<String>> progression = Map.of(
                "core::existing_chapter (Existing Chapter)", Set.of("root::intro"));

        ModIntent modIntent = new ModIntent(
                "example_mod",
                List.of("automation", "power"),
                "Integrate with early power progression",
                List.of("ExamplePack v1"));

        return new GenerationContext(questFile, spec, modIntent, List.of(example), progression);
    }
}
