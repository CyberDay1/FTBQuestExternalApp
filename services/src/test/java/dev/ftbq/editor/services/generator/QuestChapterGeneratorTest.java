package dev.ftbq.editor.services.generator;

import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.ChapterGroup;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.io.snbt.SnbtQuestMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestChapterGeneratorTest {

    private final SnbtQuestMapper mapper = new SnbtQuestMapper();

    @Test
    void generatesChaptersAndWritesDraft() throws IOException {
        QuestFile existing = createExistingQuestFile();
        QuestDesignSpec designSpec = new QuestDesignSpec(
                "Applied Energistics 2 Automation",
                List.of("Intro", "Midgame", "Endgame"),
                List.of("Require power access"),
                List.of("Tech", "Storage"),
                Set.of("minecraft:diamond"),
                List.of("item", "advancement", "location"),
                5,
                10);
        ModIntent modIntent = new ModIntent(
                "ae2",
                List.of("Certus Quartz", "Storage Buses"),
                "Integrate ME network after power setup",
                List.of("ftbquests:ae2_basics"));

        Path tempDir = Files.createTempDirectory("generator-test");
        Path examplePath = tempDir.resolve("example.snbt");
        Files.writeString(examplePath, mapper.toSnbt(createExampleQuestFile()));

        QuestFile modelOutput = createModelOutput();
        String modelSnbt = mapper.toSnbt(modelOutput);
        AiModelProvider provider = prompt -> new ModelResponse(modelSnbt, Map.of("blocks", prompt.blocks().size()));

        QuestChapterGenerator generator = new QuestChapterGenerator(provider, mapper, new PromptAssembler(), new GeneratedContentValidator());
        GenerationResult result = generator.generate(existing, designSpec, modIntent, List.of(examplePath), tempDir);

        assertFalse(result.chapters().isEmpty(), "Expected generated chapters");
        Chapter generated = result.chapters().get(0);
        assertFalse(generated.quests().isEmpty(), "Chapter should contain quests");
        assertTrue(result.validationReport().passed(), "Validation should pass with no errors");
        assertTrue(result.logs().stream().anyMatch(entry -> entry.stage().equals("draft")), "Draft write should be logged");

        long draftCount = Files.list(tempDir).filter(path -> path.getFileName().toString().endsWith(".snbt")).count();
        assertTrue(draftCount >= 2, "Expected example and generated draft files");
    }

    private QuestFile createExistingQuestFile() {
        Chapter existingChapter = Chapter.builder()
                .id("getting-started")
                .title("Getting Started")
                .icon(new IconRef("minecraft:book"))
                .background(new BackgroundRef("minecraft:textures/gui/default.png"))
                .visibility(Visibility.VISIBLE)
                .addQuest(Quest.builder()
                        .id("existing-quest")
                        .title("Existing Quest")
                        .description("Welcome quest")
                        .icon(new IconRef("minecraft:stone"))
                        .visibility(Visibility.VISIBLE)
                        .addTask(new ItemTask("minecraft:planks", 4))
                        .addReward(Reward.item("minecraft:stick", 4))
                        .build())
                .build();

        ChapterGroup group = ChapterGroup.builder()
                .id("core")
                .title("Core")
                .icon(new IconRef("minecraft:book"))
                .visibility(Visibility.VISIBLE)
                .chapterIds(List.of(existingChapter.id()))
                .build();

        return QuestFile.builder()
                .id("testpack")
                .title("Test Pack")
                .addChapterGroup(group)
                .addChapter(existingChapter)
                .addLootTable(new LootTable("starter", List.of()))
                .build();
    }

    private QuestFile createExampleQuestFile() {
        Chapter exampleChapter = Chapter.builder()
                .id("example-chapter")
                .title("Example Chapter")
                .icon(new IconRef("minecraft:book"))
                .background(new BackgroundRef("minecraft:textures/gui/default.png"))
                .visibility(Visibility.VISIBLE)
                .addQuest(Quest.builder()
                        .id("example-quest")
                        .title("Example Quest")
                        .description("Demonstration quest")
                        .icon(new IconRef("minecraft:book"))
                        .visibility(Visibility.VISIBLE)
                        .addTask(new ItemTask("minecraft:cobblestone", 8))
                        .addReward(Reward.item("minecraft:torch", 4))
                        .build())
                .build();

        return QuestFile.builder()
                .id("example")
                .title("Example")
                .addChapter(exampleChapter)
                .build();
    }

    private QuestFile createModelOutput() {
        Quest generatedQuest = Quest.builder()
                .id("ae2-intro")
                .title("Getting into AE2")
                .description("Craft the first components for your ME network.")
                .icon(new IconRef("ae2:certus_quartz_crystal"))
                .visibility(Visibility.VISIBLE)
                .addTask(new ItemTask("ae2:certus_quartz_crystal", 4))
                .addReward(Reward.item("ae2:fluix_crystal", 2))
                .addDependency(new Dependency("existing-quest", true))
                .build();

        Chapter generatedChapter = Chapter.builder()
                .id("ae2-basics")
                .title("AE2 Basics")
                .icon(new IconRef("ae2:controller"))
                .background(new BackgroundRef("minecraft:textures/gui/default.png"))
                .visibility(Visibility.VISIBLE)
                .addQuest(generatedQuest)
                .build();

        return QuestFile.builder()
                .id("generated")
                .title("Generated")
                .addChapter(generatedChapter)
                .build();
    }
}
