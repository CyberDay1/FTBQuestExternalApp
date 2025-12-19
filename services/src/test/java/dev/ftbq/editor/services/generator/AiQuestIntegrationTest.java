package dev.ftbq.editor.services.generator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.ChapterGroup;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.io.snbt.AiQuestBridge;
import dev.ftbq.editor.io.snbt.SnbtFormatter;
import dev.ftbq.editor.io.snbt.SnbtQuestMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiQuestIntegrationTest {

    @TempDir
    Path tempDir;

    private final SnbtQuestMapper mapper = new SnbtQuestMapper();

    @Test
    void aiModelResponseMatchesTemplateAndParses() throws IOException {
        QuestFile base = baseQuestFile();
        QuestDesignSpec spec = new QuestDesignSpec(
                "Steam Power",
                List.of("intro", "midgame"),
                List.of("gate-by-pressure"),
                List.of("automation"),
                Set.of("minecraft:dragon_egg"),
                List.of("item"),
                2,
                20);
        ModIntent intent = new ModIntent("Create", List.of("steam"), "Introduce boilers", List.of());

        String modelResponse = readResource("golden/model_response.snbt");
        assertDoesNotThrow(() -> mapper.fromSnbt(modelResponse), "Model response should parse via SnbtQuestMapper");

        RecordingModelProvider provider = new RecordingModelProvider(modelResponse);
        QuestChapterGenerator generator = new QuestChapterGenerator(provider);
        Path draftsDir = tempDir.resolve("drafts");

        GenerationResult result = generator.generate(base, spec, intent, List.of(), List.of(), draftsDir);
        assertTrue(result.validationReport().passed(), "Generated content should pass validation");

        QuestFile normalizedFile = QuestFile.builder()
                .id(base.id())
                .title(base.title())
                .chapterGroups(List.of())
                .chapters(result.chapters())
                .lootTables(result.lootTables())
                .build();

        String normalizedSnbt = mapper.toSnbt(normalizedFile).replace("\r\n", "\n");
        String expectedSnbt = readResource("golden/generator_normalized.snbt").replace("\r\n", "\n");

        assertEquals(expectedSnbt, normalizedSnbt, "Normalized generator output should match golden template");

        try (var stream = Files.list(draftsDir)) {
            assertTrue(stream.findAny().isPresent(), "Expected generator to persist a draft file");
        }
    }

    @Test
    void aiQuestBridgeRoundTripsFormattedOutput() throws IOException {
        String modelResponse = readResource("golden/model_response.snbt");
        AiQuestBridge bridge = new AiQuestBridge(mapper, new SnbtFormatter(mapper));

        QuestFile parsed = bridge.parse(modelResponse);
        String formatted = mapper.toSnbt(parsed);
        QuestFile roundTripped = mapper.fromSnbt(formatted);

        assertEquals(parsed.chapters().size(), roundTripped.chapters().size(),
                "Round-tripped file should have same number of chapters");
        assertEquals(parsed.chapters().get(0).quests().size(), roundTripped.chapters().get(0).quests().size(),
                "Round-tripped chapter should have same number of quests");
        assertEquals(parsed.chapters().get(0).title(), roundTripped.chapters().get(0).title(),
                "Round-tripped chapter should have same title");
    }

    private QuestFile baseQuestFile() {
        ChapterGroup group = ChapterGroup.builder()
                .id("core")
                .title("Core")
                .icon(new IconRef("minecraft:book"))
                .visibility(Visibility.VISIBLE)
                .chapterIds(new ArrayList<>(List.of("existing")))
                .build();

        Quest existingQuest = Quest.builder()
                .id("welcome")
                .title("Welcome")
                .description("Start here")
                .icon(new IconRef("minecraft:paper"))
                .visibility(Visibility.VISIBLE)
                .tasks(List.of(new ItemTask(new ItemRef("minecraft:stick", 1), true)))
                .rewards(List.of(Reward.xpLevels(3)))
                .dependencies(List.of())
                .build();

        Chapter existingChapter = Chapter.builder()
                .id("existing")
                .title("Existing Chapter")
                .icon(new IconRef("minecraft:book"))
                .background(new BackgroundRef("minecraft:textures/gui/advancements/backgrounds/stone.png"))
                .visibility(Visibility.VISIBLE)
                .quests(List.of(existingQuest))
                .build();

        return QuestFile.builder()
                .id("example_pack")
                .title("Example Pack")
                .chapterGroups(List.of(group))
                .chapters(List.of(existingChapter))
                .lootTables(List.of())
                .build();
    }

    private String readResource(String name) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("Missing resource: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class RecordingModelProvider implements AiModelProvider {
        private final String response;

        private RecordingModelProvider(String response) {
            this.response = response;
        }

        @Override
        public ModelResponse generate(AiGenerationRequest request) {
            return new ModelResponse(response, Map.of());
        }
    }
}
